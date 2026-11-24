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

## Entry 21: 0.9.15, P5: switching tags glides instead of jumping (2026-11-17, Opus 5.5)

| What | How |
|---|---|
| Tag strip | One selection slides from chip to chip (a shared element) with a spring. It follows the chip it is going to while the row scrolls, and a new choice mid-way starts from where it is drawn, so it never jumps. In the theme it takes the chips' selected colour and corners; the chips sit as text on the strip's one glass, as a segmented control (the theme's chip fill is opaque and would hide the slide). Several tags on: each selected chip is filled, as before |
| Drawer list | When a tag (or the query) changes the list, apps that stay glide to their new places, the others fade out and the new ones fade in (`animateItem`, scaled by Settings → motion speed) |
| Tag list | Already a container transform: it grows out of its button and shrinks back into it, interruptible (since 0.6); unchanged |
| Home pill and drawer field | Already one morphing element when the pill is at the bottom (0.7.1, "The pill becomes the search field"); unchanged |
| Commands in the drawer field | Typing `!` or `?` first in the drawer's field hands over to the search page with what was typed, where `!next`, `!exam`, `!plan`, `!abs` and questions to Claude run (the same path as the field's Ask Claude button) |

---

## Entry 22: 0.9.16, C5: notifications wait during study blocks (2026-11-17, Opus 5.5)

Settings → Study companion → "Quiet during blocks" (off by default; it needs the system's notification access, and
a card there opens that switch and says whether it is on).

| What | How |
|---|---|
| Held | While a block runs (and is not marked done or missed), a notification that is not let through is snoozed until the block ends: Android gives it back then, so nothing is lost (`QuietService`, a notification listener) |
| Let through | Calls, alarms, navigation, music and anything ongoing; the system and the launcher; apps with the tag "important" (setting); messages naming one of your people (whole name, so "Ali" is not "Alice"); mail for your school address (Gmail shows the account; setting, e.g. `@school.tn`) |
| After the block | A notice on the home screen: "Micro is over · 14 notifications waited", with "WhatsApp 6 · Instagram 5 · Gmail 3 · back in your notifications" |
| Tests | `QuietRulesTest`: what gets through, names as whole words, school mail by account, tags, the summary |

Emulator check (added with 0.9.21, `ci/smoke.sh` 14d): a block running now, access given with
`cmd notification allow_listener`, the setting switched on in Settings, a notification posted from the shell; it must
be held. Risk: MagicOS may stop the listener in the background; the launcher is the home app, which usually keeps
it alive. If notifications ever stay away after a block, turn the setting off: snoozed ones come back on their own
at the end of the block in any case.

---

## Entry 23: 0.9.17, P7: other apps' widgets in the launcher's look (2026-11-17, Opus 5.5)

The launcher now hosts other apps' widgets in a view of its own (`LiquidWidgetView`), so after every update the app
sends, and whenever the widget's views change (the rows of a list arrive later than the widget), its views are gone
over again (`WidgetRestyle`):

| What | How |
|---|---|
| Fills | Solid card and row fills (colours, filled shapes, layers of them, the solid part of a press ripple) give way to the wallpaper or to the widget's glass plate ("Glass: on") |
| Text | Takes the launcher's text colour for that page (light or dark by the wallpaper), as opaque as it was; grey or see-through text stays lighter, so titles and details keep their order |
| Switches | Settings → Home screen → "Other apps' widgets in the launcher's look" (on); one widget: its edit chips, "Colours: launcher / the app's" |

Cannot be restyled, and stays as the app draws it: pictures (photos, album art, maps, and widgets that send their
whole content as one image, as some clocks and weather widgets do), colours inside images and icons, and anything a
custom view of the widget draws itself. The app re-applies its own colours with every update; the launcher's look
follows within the frame. Not measured on the phone.

Not done for P7: the options of the launcher's own widgets (layout, density, font, accent, corners, fields).

---

## Entry 24: 0.9.18, A4's battery rule (2026-11-17, Opus 5.5)

On the trip home (the commute layout is on, entry 17) with the battery under 30 % and not charging: the glass is
drawn frosted (no shader pass), other apps' widgets rest behind a small "Resting to save battery" card (they update
and draw on their own), and the moving wallpaper stops; a notice says so once. Everything comes back when you are
home (the trip ends) or the phone charges. The battery is read from the system's battery broadcast only while a
trip is on.

---

## Entry 25: 0.9.19, C4's annales (2026-11-17, Opus 5.5)

Settings → Study companion → "Annales for exam week": choose a folder (the system's folder picker; the launcher
reads only that folder, through the documents provider, with no storage permission) holding one sub-folder per
module with its past papers as PDFs (one more level is read, for "Module/2024/…"; PDFs at the top are "Other").
The card says how many modules and PDFs it found. In the seven days before an exam, the search page shows the card
"Annales" (new, after the exam countdown; in your order if you changed the cards, add it there): one chip per module
with its count, the module of the next exam first and open (its name matched loosely to the timetable's subject),
its newest six papers, a tap opens one in your PDF app.

Not as the spec says: "one folder per module" is not a home-screen folder (the launcher's folders hold apps); the
card is in the search page instead, one swipe down. The exam layout (C4, 0.9.11) can still filter the drawer.

---

## Entry 26: 0.9.20, P11 (part): one motion speed, one haptics switch (2026-11-17, Opus 5.5)

| What | How |
|---|---|
| Speed | 26 springs set a fixed stiffness and ignored Settings → motion speed (the day ring, the battery ring, the letter index's bubble and marker, the search cards growing, the flashcard's turn, the calendar's month slide, ticks, the hub's lists, the ask-Claude turn, the drawer's pull); they now all go through `MotionScale`, like the drawer, search, hub and sheets already did. The new tag slide and theme colours too |
| Haptics | The letter index's ticks had their own switch only; they now also obey the launcher's haptics switch. The tag strip's hold and the field's tag swipe used the system haptic directly; they go through `Haptics` (the switch, the strength) like the rest |

Already coherent before this pass: drawer, search, hub, sheets and overlays use the shared springs of `Motion.kt`
(calm, liquid or lively) and keep their speed when turned round (D125); fades stay short tweens, which never
delay what comes next. Not done: measuring frame times on the phone (Diagnostics → glass benchmark covers the glass
only).

---

## Entry 27: 0.9.21, P8: layouts for a while (2026-11-17, Opus 5.5)

Hold a layout in the layouts sheet: "Switch to it for 30 minutes / 1 hour / 2 hours". When the time is up (checked
on the home screen's minute tick) the launcher goes back to the layout that was on, through the usual layout
transition, unless you chose another layout meanwhile. The sheet then says "Until 15:40, then Home" with "Keep it"
to stay. `LayoutTimerTest` covers the way back and the hand switch.

---

## Entry 28: 0.9.22, P7: options for the date and battery widgets (2026-11-17, Opus 5.5)

In edit mode, the date and battery widgets have a "Customise" chip (as the clock has): a sheet with size (small,
medium, large), weight (light to bold), colour (text or accent), font (the clock's or the launcher's) and the parts
shown (date: weekday, month, year; battery: ring, percentage, "charging"). Each choice is written to the widget at
once (`setItemOption`), so it changes behind the sheet. The calendar, Now card and Today widgets already have their
settings (Settings → Home screen, their own screens); their per-widget options are left for later.

---

## Entry 29: 0.9.23, P7: the calendar widget's own options (2026-11-17, Opus 5.5)

The calendar widget's "Customise" sheet (edit mode): its own card (as for all widgets, a glass card, or on the
wallpaper), corners (small, medium, large), colour (text or accent) and parts (the month grid, or the compact
week; the next events under it). Live on the widget, as for the date and battery widgets.

---

## End of this session (2026-11-17): state for the next model

**Version shipped:** 0.9.23 is the last push of this session. Released and verified on the emulator by CI: 0.9.0 (run 62),
0.9.1 (run 63), 0.9.2 (run 64, second attempt; the first failed on a slow emulator, see entry 11). 0.9.3 and 0.9.4 were
pushed together (run 65); 0.9.5 to 0.9.9 together (run 66, passed); 0.9.10 and 0.9.11 together (run 67, passed); 0.9.12 (run 68: built, unit tests passed, the real glass shader compiled on the emulator, but not released: my new check of the tuning panel found no screen dump, because the bench kept moving after measuring and the screen never settled; 0.9.13 keeps it still unless you press Move); 0.9.13 (run 69: did not compile, a smart cast across modules in `Controls.kt`; fixed) and 0.9.14 (run 70: the editor's name clashed with Settings → Theme's; fixed) released together by run 71 (passed, emulator included); 0.9.15 to 0.9.18 by run 72 (passed); 0.9.19 to 0.9.21 by run 73 (passed; the C5 check found the listener connected but not the setting's row, its text being title and summary together: fixed in the test); 0.9.22 and 0.9.23 after it. Check the GitHub releases for the newest `v0.9.x`; if a
run failed, its log says where (`ci/smoke.sh` prints PASS / FAIL per check). Nothing of this session was tried on the phone.

| Workstream | State |
|---|---|
| Audit | Done (top of this session) |
| P1 a. redirect during calls | Done (0.9.0) |
| P1 b. drawer / keyboard ghost | Done (0.9.0) |
| P2 study hub, pill, clock notice, A2, A3, B4 | Done (0.9.1, 0.9.2): hub grown out of the pill, drag to close, timeline, arrival, shift tonight, notes, templates, check-in; the pill's study item fully customisable; the clock line with one action |
| P3 search page | Done (0.9.3): study-first cards in your order, calendar never cut, commands. The transition was already finger-driven and interruptible; unchanged |
| P4 real liquid glass, WM Keyboard theme engine | Done (0.9.12 to 0.9.14): the real glass (AGSL: Snell refraction by the edge's shape, dispersion, swell, specular rim, depth shading, grain) with Tune the glass (live sliders, presets); the theme engine after WM Keyboard's model, your backup as the default theme of every button, chip and round button, import/export, the editor, accent and glass colour. Menus and popups keep the glass (question 8) |
| P5 drawer | Mostly: bigger tag button, field with the study glance and Ask Claude (0.9.8); the tag selection slides between chips and the list glides when tags change (0.9.15); the tag list was already a container transform and the pill already morphs into the field. commands typed in the drawer field go to the search page (0.9.15). Not: the top tabs pill rising out of the home pill |
| P6 scroll index | Done (0.9.6): the rail |
| P7 widgets | Partly: no glass box behind the built-in widgets by default (0.9.10); other apps' widgets in the launcher's look, re-applied after every update, with a global and a per-widget switch (0.9.17); size, weight, colour, font and parts for the date and battery widgets (0.9.22); card, corners, colour and parts for the calendar (0.9.23). Not: per-widget options of the Now card and Today widgets |
| P8 layouts and wallpaper | Partly: context triggers for layouts (trip home, exam week; 0.9.11); a layout for a while, then back (0.9.21). Not: layout previews beyond the miniatures, per-layout wallpaper choice UI beyond "give it the current wallpaper", time windows, the LayoutsSheet redesign, per-wallpaper blur/dim/saturation/vignette, tone feeding the glass, wallpaper quick tools |
| P9 icon animations | Done (0.9.4) |
| P10 rest of Phase 5 | Done: A4 (layout switch + trip line), A5, A6, B1 (+ ≈), B2, B3, C1, C3, C4 (layout switch), C5 (0.9.16), A4's battery rule (0.9.18), C4's annales (0.9.19, as a search card), D3, D4, E1, E2, E3, E4, E5, launcher-data. Phase 5 is complete |
| P11 motion pass | Partly: drawer, search and hub springs keep their speed when turned round (D125); every spring follows the motion speed and every haptic the haptics switch (0.9.20). Not: frame-time measurements on the phone |

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

**Exact next step:** P7, the Now card and Today widgets: the same "Customise" sheet as the date, battery and
calendar widgets (`WidgetOptions.kt`: `WidgetLook` and `WidgetParts`), with their parts (Now card: tasks, buttons;
Today: tomorrow, tasks) and corners for their glass card (`StudySurface`).
After that P8 (layout previews, per-layout wallpaper choice, time windows, per-wallpaper blur/dim/saturation), then
the last bits: the drawer's top tabs pill rising out of the home pill (check it on the phone: it depends on where
both pills are drawn), and the rest of the P11 pass (frame times on the phone).
---

## Session of 2026-11-17, evening (Opus 5.5): 0.10.0 safety release

Working branch: `claude/exciting-knuth-s83frp-1jp272` (fast-forwarded from `claude/exciting-knuth-s83frp`, tip `58a090c`).
The old branch gets the log commits too (marked to skip CI), so "continue from FABLE_LOG" works from either.

**Local type check (new, saves CI cycles):** Google's Maven is blocked, but Maven Central has JetBrains' Compose
desktop 1.8.2, Robolectric's `android-all` 17 and the backdrop library's desktop jar. A harness in the session's
scratchpad (`tc/tc.sh`) compiles every source with Kotlin 2.4.10 against them plus ~15 stubs (androidx.core,
activity, datastore, `LocalContext`, the R class) in ~20 s and prints only errors not in a baseline of 14 known
API-gap ones; `tc/ut.sh` compiles core/common, core/settings, core/data and feature/redirect with their tests and
runs JUnit locally (188 tests). `tc/modlint.py` checks module boundaries. The scratchpad is not in the repo: the next
session can rebuild it from this description (`fetch.sh` downloads the jar set with retries; Maven Central rate-limits).

**W0.** Run 74 (0.9.22 + 0.9.23) built and passed the unit tests; the emulator failed checks 2 and 3 (Home pressed
while the accessibility service re-subscribed after a screen dump). The test now waits for the service to be ready,
prints the redirect trace on failure, and expects the evening check-in on the clock line after 22:30 (run 75 ran at
22:46). CI publishes a release only when the pushed head commit's message contains the release marker; release
notes are the version's CHANGELOG.md section (`ci/release_notes.py`), checked before building.

**W1.** `CHANGELOG.md` (0.9.0 to 0.10.0, plain words), `core/common/Changelog.kt` (+ test against the real file), the
Updates screen shows a newer release's notes and has "All changes" (Changelog screen), a one-time What's new card
(`feature/home/WhatsNew.kt`); CI bundles the changelog into the APK's assets. Emulator check 8b.

**W2.** `feature/home/Sheets.kt`: `LiquidSheet` (≤ 85 % of the space above the keyboard, fixed header/footer/actions,
scrolling body, keyboard lifts it, `bringIntoViewWhenFocused`), `SheetField`, `SheetCheckRow`. The per-app Tags dialog
became `AppTagsSheet` (field fixed at the bottom with a + button); the drawer's tag list fills the height and ends
above the drawer pill (`controller.drawerPillTop`); layouts and widget options use the sheet; app menu, tab menu and
folders scroll; dialog bodies scroll. Emulator check 9c types 30 tags and checks the field and Done stay on screen.

**W3 (started, not in 0.10.0's behaviour):** the theme model is in: `core/data/ThemeDoc.kt` (the theme kept as WM
Keyboard's own JSON, every field round-trips, the launcher's additions under `liquidLauncher`), `ThemeFields.kt`
(every option once: group, section, title, meaning, range, condition), `ThemeResolve.kt` (WM's derivation rules and
material presets, per-colour wallpaper roles), `core/common/WallpaperPalette.kt` (dominant, vibrant, muted, dark,
light from the picture). The drawing, the lab and the migration are in progress on top of 0.10.0.

**Questions for Med (this session so far)**
1. Only 5 of the 8 screenshots arrived (the wallpaper quick strip, the drawer alone and the search page at the
   calendar are missing). I worked from your text for those.
2. Screenshot 1: the green arrows point at the album chips (Recent, Favourites…) and at Place and Add to
   favourites, while your text lists Place and Add to favourites as red. I took the text: everything goes through the
   theme, and the chips' current look (your keyboard's chip colours) stays the default.
3. The blue you marked is your Proba 2 block colour (Google Calendar's Blueberry, #5484ED). It is now the fixed
   default accent; the main button keeps the block's colour as before ("Main button colour").

## Session of 2026-11-18 (Opus 5.5): 0.10.1 final release

**Released:** 0.10.0 (the safety release, run 78, published after a re-run: the first attempt hit the emulator's
"isn't responding" flake) and **0.10.1** (run 85, published 2026-11-18 09:34 UTC on its third attempt). Two releases,
as agreed.

The first 0.10.1 release run (83) published nothing: four emulator checks failed. Two were real bugs of this session:
the top tabs pill's rise (P5) moved the drawer's invisible tabs over the home pill while the drawer was closed, so they
took the pill's touches (in edit mode, the edit bar's: Pages and Widgets did nothing); and the collapsed hub's
"The whole day" sat at the end of its list, out of sight. Fixed (no rise while the drawer is closed; the chip above
the list). The second attempt (run 84) passed those, but its tap on the block's Done missed in the
collapsed hub (with the chip above the list, the button sat at the hub's lower edge): the collapsed hub is now at
most half the screen, its block shows its buttons right after its steps (the notes, cut to two lines, under them),
and check 14b opens the whole day before ticking (noting whether Done is in sight when collapsed). Run 85 then
passed every check of this session; its first two attempts failed only checks 2 and 3 (Home pressed right after the
first start: the redirect engine saw no window event, or saw it and did not act), which passed in runs 80, 83 and 84
with the same redirect code, and the third attempt passed everything. That start-up flake is worth a look: see the
next step.

**Workstreams**

| | State | What |
|---|---|---|
| W0 | Done | Releases only on the marker, notes from CHANGELOG.md (0.10.0). |
| W1 | Done | Changelog, release notes on the Updates screen, What's new once (0.10.0). |
| W2 | Done | Sheets and menus never overflow (0.10.0). |
| W3 | Done, with the limits below | Theme document (`ThemeDoc`, `ThemeFields`, `ThemeResolve`, `ThemeStore`), the Theme lab (`ThemeLab.kt`), drawing (`ThemeDraw.kt`, `ThemeSurfaces.kt`, `ThemePress.kt`), migration `migrate010` (accent fixed blue unless chosen, main button colour kept, dark pills where a theme says nothing). Picture and stickers behind every sheet, material on the pills, menu row height and gaps in real rows. Frame meter. |
| W4 | Done | `SettingsMap` (7 entries, 26 pages, 4 tabs; every one of the 365 options placed, the internal ones on a hidden page; keys unchanged; 0.9 section ids map to pages), `SettingInfo`, `Pages.kt` (entry and page screens, sticky preview, tab chips with counts, Reset per tab), rows held for Reset / Explain / Copy id, greyed rows with the reason (`SettingDependencies.whyOff`), search and Recently changed open page + tab + highlight, `page:<id>[:<tab>]` links, "Customise" chip from home (dock, folder, search page, hub, pill on its default). Emulator check 14d goes through search. |
| W5 | Done | Backup and Claude page (System): Back up now (wallpapers optional, theme pictures always), daily backup to a SAF folder with JobScheduler keeping N copies (`AutoBackup`, `BackupJobService`), restore preview (`LauncherData.previewBackup`), `SettingsAI` (Markdown export with the answer rules, Send to Claude by ACTION_SEND with a question, paste, check, apply valid entries, red for invalid, one-tap Undo). No network. |
| W6 | Done | Select in the drawer's app menu, picking with checks, the bar (Tag, Untag, Hide, Add to home), bulk tag sheet, a tag's own list of all apps with checks and search. Emulator check 9d. |
| W7 | Done | Hub opens collapsed (≤ 50 % of the screen; the hero block brief: its buttons right after its steps) with a second spring to the whole day (chip, handle, pushing the list up); "Study hub opens"; dim, blur and text colour of the hub (auto-contrast); the clock's study line auto-contrast + soft halo or clock / white / black. The rail part was already done in 0.9.6 (short liquid rail ≤ 180 dp, edge letters kept as "String"). |
| W8 | Done | P5: the top tabs pill rises out of the home pill. P7: Now card and Today widget Customise sheets. P8: layout for a time of day (from, until, days, over midnight), a look per wallpaper (blur, dim, saturation, vignette), glass tint "The wallpaper's colour", real layout previews, any library picture as a layout's wallpaper ("Choose its wallpaper…"). |

Decisions of this session: D139–D143 in DECISIONS.md (theme document, settings map, Claude patches, daily backup,
frame meter).

**Cost of the moving background and textures.** The emulator draws in software, so its frame times say nothing about
the phone. Instead, `app/FrameMeter.kt` listens to Android's own frame metrics while the home screen is in front and,
every ~10 s of continuous drawing, writes one line to the debug log: frames drawn, late frames (missed deadline),
average and worst frame time, GPU time, and what was on screen (moving background kind, amount, speed, size; the
theme's textures, material, sheet picture, frost blur). By construction: a moving background records 3 to ~120
primitives per frame on the UI thread (orbs: 3–7 radial gradients; starfield and fireflies: up to ~120 dots) and stops
its clock whenever an app, the drawer or search covers it, or on battery saving; textures are drawn from one cached
tile per control (no per-frame decoding); the sheet picture's blur is a layer recorded only when its size or picture
changes. No measured numbers from the HONOR yet: they will be in Settings → System → Debug log ("Frames:") after a
minute on the home screen with a moving background.

**Known limits and bugs**
- Theme: the SINK press motion does not dip the neighbouring buttons as WM Keyboard does; with Individual pills the
  drawer pill blends its fill during the morph but switches its glass values at the end; the "meld" splash has no
  launcher equivalent and is not offered; keyboard-only options are kept in the file but not shown (see 0.10.1's
  changelog).
- Settings: the "Customise" chip is not offered on the drawer's empty area (icons cover it; the drawer's tab menu has
  "Customise the drawer").
- Backup: the daily job runs when Android lets it (about once a day, battery not low). A backup without wallpapers
  restored on another phone keeps only the library pictures that phone has.
- Picking apps: only from the drawer (not from search results); the bar covers the drawer's first row while picking.
- Hub: pulling down from the whole day closes the hub (not to the collapsed height first).
- Wallpaper look: saturation and vignette apply to the home screen, not to the copy handed to the system (lock screen).

**Questions for Med**
1. The search bar and the other pills are dark for every theme that does not set them, your keyboard backup included
   (its space bar colour is nearly transparent). Keep it, or follow the keyboard's space bar?
2. The hub now opens collapsed. Is half the screen the right collapsed height?
3. The line under the clock now picks dark or light for the wallpaper, with a halo (before: the clock's colour). Keep?
4. Backup and Claude is in System. Would you rather have it under Wallpaper and layouts?
5. "Send to Claude" goes to the app in "App that gets Tell Claude" (com.anthropic.claude by default), else the share
   sheet. Right app?

**After the release:** Med used 0.10.1 and wrote his feedback; it is the brief for the next session:
`docs/NEXT_SESSION_PROMPT.md` (pills back to liquid, his keyboard theme as the default with wallpaper colours, the
pill's long press, the hub opening full with folded cards, the clock line in the clock's colours, a calmer search
page, the tag list from the bottom, a movable and smoother letter index, two clock designs). His answers: everything
follows the wallpaper, accent included; switch his phone to the new default (current look kept as a preset); the hub
opens full, cards folded; the search page with fewer cards. The local type check is now in the repo:
`tools/typecheck/` (see its README).

**Exact next step:** follow `docs/NEXT_SESSION_PROMPT.md`. Before that brief existed the next step was: make emulator checks 2 and 3 wait for the redirect engine's first window event after the first
start (they flaked twice in run 85 before passing; a slow first start, not a code change, as far as the logs show); then the theme's limits above (SINK dipping the neighbours first, then the Individual pills'
glass during the morph). Read the "Frames:" lines Med's phone writes and put the numbers in this log; if a moving
background or texture is late on more than 1 % of frames, lower its default amount.

## Session of 2026-11-18, afternoon (Opus 5.5): 0.10.2 and 0.10.3, Med's feedback on 0.10.1

Brief: `docs/NEXT_SESSION_PROMPT.md` (items 1 to 11). Working branch: `ccr-f4d083cc-vpyudk` (the branch this session was
given; fast-forwarded from `claude/exciting-knuth-s83frp` at `51ecd76`; the `claude/…` branches were not touched). CI
now also builds `ccr-*` branches (D148), so releases still come from the session's branch through the marker.

**Released:** 0.10.2 (run 88, published 2026-11-18 14:42 UTC, every check passed on the first attempt) and 0.10.3
(run 89, published 14:59 UTC after one re-run of its emulator job: the first attempt stopped before the emulator
started, the SDK's system image download broke with "Premature EOF"; the re-run passed every check). Two releases,
as agreed. Run 87 (items 3 to 7, no release) failed one new check only, fixed before 0.10.2. The new check that opens
the clock gallery only wrote a note in run 89 (in edit mode a widget shows "Customise" once it is tapped); the test
now taps the clock first (found by its digits in the screen dump). Run 90 (no release) passed every check, that one
included; its ASCII looks (`12-clock-Tall`, `12-clock-Stacked`) show Tall's narrow, tall digits with the two dots and
Stacked's two heavy lines, hours over minutes.

**Items**

| # | State | What |
|---|---|---|
| 1 | Done | Pills: no fill of their own (the theme's key colour over its frost, as WM Keyboard's space bar); the search bar has its own tint, a third of 0.10.1's (`liquidLauncher.space.searchBackground`, Theme lab › Colours › Pills › "Search bar tint"); "Material on the pills" (off by default). `ThemeStore.migrate0102` removes 0.10.1's `0xA6141218`/`0xFFF2F2F5` only where they are still exactly that (the theme, each individual pill, the look kept from before 0.10). D146. |
| 2 | Done | `ThemeDocs.Default` is `wallpaper-stoped.wmtheme.json` field for field plus the launcher's block: every colour follows a wallpaper role with its own opacity (`ThemeDocs.defaultFollow`), main button "plain" (vibrant). Deviations: sheets show the wallpaper (`frost.backdrop` AUTO), the swoosh splash left out, id `liquid_default`. Texts that follow the wallpaper or are Auto are made readable (D145; `WallpaperPalette.average` is new and refreshed once). The phone switches once; the look before is the lab preset "Before 0.10.2" (variants carried over); Blueberry is a one-tap accent in the colour dialog. The 0.10.1 default stays as `ThemeDocs.Legacy010` for converting 0.9.13 themes. D144. |
| 3 | Done | A long press on the pill runs its action; `LauncherAction.CustomisePill` is offered in the pill's four gesture pickers only (`ActionSetting.pillActions`). `LauncherAction`'s lists became lazy (a class-initialisation cycle could put null in them). |
| 4 | Done | "Study hub opens" defaults to the whole day; cards follow "Blocks in Today" (the hero no longer forced open); the timeline leaves out the hero; header count beside "Today", date on its own line; chips and a card's buttons wrap (FlowRow). |
| 5 | Done | "Colour of the line under the clock" defaults to "Like the clock", which now really is the clock design's colour (`clockColours`, shared with `ClockFace`); the ring and the ✓ take it too, with the clock's own shadow. |
| 6 | Done | Default cards: `glance` ("Today · 24 days to …", opens the widgets and the exam countdown), `next` (with the day ring), `claude` (one chip, prompts under its arrow), `apps`. `!` lists the study commands, `?` alone your prompts. Rows wrap, the stack fades under a soft top edge. D149. |
| 7 | Done | The drawer's tag list from the bottom (reverse list: pinned, most used, then A to Z at the top; best match nearest the field); the field above Clear and Done; pin left, tick right. The field is labelled for screen readers (run 87's check could not find it). |
| 8 | Done | "String: top end", "String: bottom end" (only that stretch takes the finger), "Letter colour" (wallpaper by default); per-edge springs, a cross-fade between edges, proximity-based growth and bold; a lane in the grid for letters shown at rest; the frame meter counts index drags apart (`FrameCost.activity`, lines from 30 frames). |
| 9 | Done, depth not | Designs "tall" (Tall) and "glance" (Stacked) in `Clocks.kt`; the old "stacked" keeps its id as "Two lines". Depth effect not done: it needs the wallpaper's subject cut out, and no offline way does that well for Med's pictures (ML Kit's subject segmentation downloads its model through Play services at first use; the bundled selfie segmentation only knows people in photos, not illustrations). A real alternative would be a mask Med paints once per wallpaper; not started (question 7). |
| 10 | Done | "Already there" below; the looked-for study options moved to their page's first tab; settings search finds page editors (`SettingsMap.editors`). |
| 11 | Partly | Done: Export for AI and the dots count only real changes (D147); CI warms the redirect engine up before checks 2 and 3 (they passed first time in runs 86 to 88); failed checks are listed at the end of the emulator output; a CI look at the two clock designs; individual pills blend their glass during the morph (`liquidGlass(…, quality) { style }` reads the style while drawing, `lerpGlass`). Not done: "Frames:" numbers from the phone (no debug log this session); SINK's neighbours and picking from search (plans below). |

**Already there (item 10)**, as it was before this session:
- Pill colour: Look › Theme lab › Colours › Pills › Fill. Setting it to Auto would have removed the dark fill; 0.10.1
  had written the dark value into the theme itself, so it showed as "set".
- Colours from the wallpaper: Theme lab › any colour › "Follow the wallpaper" (dominant, vibrant, muted, dark, light).
  Your keyboard theme: Theme lab › Presets › "my backup" (nice liquid, not "wallpaper stoped").
- Long press on the pill: Home › Pill › Behaviour (Settings by default, hijacked by the Customise chip; any other
  action already worked).
- Study hub opens, Blocks in Today: Study › Study companion › Layout.
- Colour of the line under the clock: Study › Study companion › Look (now Layout); "Like the clock" existed but used
  the home's text colour.
- The search page's cards: Drawer and search › Search › Layout (the card editor; search could not find it before).
- Rail position: Drawer and search › Letter index › Layout (rail only). Nothing existed for the string's ends, the
  letter colour, the tag list's order, or the clock designs.

**Frames.** No debug log from the phone this session, so no phone numbers yet. The emulator's lines (software
drawing, meaningless for the phone) show the meter works with the new theme, e.g. run 86: "600 drawn, 599 late,
256.7 ms on average; material LETTERPRESS (PAPER); frost blur 9". On the phone: a minute on the home screen with a
moving background, then a few drags of the letter index, then Settings › System › Debug log; the lines start with
"Frames:" (the index's end with "letter index (string…)").

**Known bugs and limits**
- The two clock designs are only seen as CI ASCII peeks (`12-clock-Tall`, `12-clock-Stacked`); Tall's bevel (1.6 %
  of the font size) and Stacked's thickening (3 %) want a look on the phone.
- Readable text is judged against the wallpaper's average colour; over a very mixed picture a pill can still pick the
  weaker ink (the drawer's and the search page's pills re-check against their page).
- With "Both edges" the index lane is kept on both sides (about 10 dp each).
- "Material on the pills" absent counts as off: an imported theme with a material no longer dresses the pills (0.10.1
  did).
- Old limits still open: SINK does not dip the neighbouring buttons; picking apps only from the drawer; pulling down
  from the whole day closes the hub. (Individual pills now blend their glass during the morph.)

**Questions for Med**
1. Your keyboard theme says "Behind the glass: Nothing", which in the launcher makes sheets solid. I made sheets show
   the wallpaper (your board colour is fully see-through). Keep?
2. Your keyboard's Letterpress material (paper grain, inset edge) now dresses the buttons; the pills stay clear glass
   ("Material on the pills" off). Keep?
3. The keyboard's swoosh splash was left out so your own touch effect (swoosh, rainbow) stays. Want the keyboard's?
4. Text turns to a dark (or light) ink below a 3:1 contrast. Too eager, or not enough?
5. Stacked clock: its tint is the wallpaper's vibrant colour, softened (lightness 86 %, 78 % on a light wallpaper).
   Right?
6. Search page: your prompts are under the Ask Claude chip's arrow (and on "?"). Rather shown at once?
7. Depth effect: would you paint the subject once per wallpaper (a mask) so the clock can pass behind it?

**Exact next step:** read Med's feedback on 0.10.3 and his debug log's "Frames:" lines (write the numbers here; if
a moving background, texture or the letter index is late on more than 1 % of frames, lower its default). Then the old
limits: (1) SINK: a shared touch field read by each themed button's `graphicsLayer`, dipping by distance (the
dynamic glass of `Glass.kt` shows how to read per-frame values while drawing); (2) picking from search results: a long
press on an app result starts the drawer's picking with that app.

## Session of 2026-11-20 (Opus 5.5): 0.11.0, Med's review of 0.10.3

Brief: Med's review of 0.10.3 in the chat (two debug logs, 2026-11-19 19:23 to 2026-11-20 16:35; his settings export;
three pictures: his sketch of a circle of arcs, his drawer, the wallpaper screen). Before any code he asked for my
understanding, recommendations and questions; his answers: the circle sits where the Calculator is in his drawer (in
reach of one hand), turns like a knob (the clock face stays a choice), the edge is left to Back, "fade" meant hidden
when not in use, and for icon colours the better guess (not a switch to wallpaper colours). Branch `ccr-f4d083cc-vpyudk`.

**Released:** 0.11.0, by the CI run of the commit "0.11.0 [release]: …" that carries this entry. It holds everything
below: the 0.10.4 planned in the chat (fixes first, circle after) was folded into it, because both were ready at once.
CI before it: run 91 (the first batch) failed 18 checks with 11 "not responding" dialogs and no crash: the emulator
was too slow that day (run 92, the same code and more, had no dialog and passed every one of those checks). Run 92
failed only the new circle check: it could not switch the index to String, because the Letter index page's preview
never stopped moving and uiautomator cannot read a screen that never goes still. The preview now runs twice and rests
(by the clock, not by frames), and CI waits for it. Run 93 (the code released) passed every check, the circle's
included: it found the circle, turned it ("letter circle: turned from C to G (knob)") and the frame meter's line named
the screen ("drawer; letter circle"). Frames drawn in 3 s by a screen at rest: home 1, drawer 0, search 6 (the
field's cursor), wallpaper screen 10, study hub 0.

**Frames on the phone** (Med's debug log; "late" against the frame's own deadline)

| While… | Late frames | Time per frame | GPU |
|---|---|---|---|
| Home at rest | 3–10 % | 22–30 ms | 9–13 ms |
| Drawer open | 7–17 % | 27–38 ms | 9–13 ms |
| Dragging the letters (string) | 20–50 % (a few windows 0.5–4 %) | 35–55 ms | 5–9 ms |
| Around the study hub (I'm home, shifting blocks, ticking tasks, a study link, Ask Claude) | 30–81 % | 55–103 ms | 10–14 ms |
| Wallpaper screen | 16–39 % | 34–60 ms | 15–27 ms |

Worst single frames: 300–430 ms right after the launcher comes back to the front, 100–200 ms on the first drawer
opening after that. Dragging the letters is CPU-bound (GPU 5–9 ms). On the emulator every screen at rest draws 0 to 6
frames in 3 s (CI's new idle count), the study hub included: no endless animation there; the hub's 600 frames in 30 s
on the phone came from use, at 55–103 ms each, so the cost is per frame. The emulator's own frame times mean nothing
for the phone (software drawing).

**Items**

| # | Med's point | State | What |
|---|---|---|---|
| 1 | Search: the field goes above the keyboard, then down | Done | Only the keyboard's opening height is remembered (his log: 1041 px saved after typing, 933 px at the next opening); a guess that is off eases with the keyboard's rise (smoothstep); a dropped keyboard request is made again after 200 ms (13 opens needed one). D151. |
| 2a | Drawer: no option to make the letters fade | Done | It existed (Letter index › Look › "Show the letters"; his was "Always") but settings search found nothing for "fade". Now on the Layout tab, found by fade/hide/hidden; the default is "Hidden until you use them", and "Always" became that once. |
| 2b | Drawer: sometimes lags | Partly | A long letter jump skips the rows in between (jump past 3 rows, glide the last 1.5); the hub's scrolling buttons draw without sampling the glass and its fixed ones stop redrawing on scroll; "Frames:" lines name the screen. Not measured on the phone yet. D155. |
| 2c | Drawer: the side margins no longer work | Done | 0.10.3's lane for the letters (27 dp at letter size 14) overruled the margin on their side; the margin counts again, the lane is only added with "Always". |
| 3 | The circle | Done | `LetterDial.kt`: three rings of arcs in a dark, middle and light shade of the wallpaper's vibrant colour (or the accent, or his own), a soft shadow, the rings turning with the thumb; the current letter shows in its middle. It drives the string through `IndexDrive`, so the letters, bubble, highlight and glide are the same as under a finger. Knob by default (`DialMath.knob`, one turn for A to Z, adjustable), clock face as a choice; 85 % opaque, 25 % while the list scrolls; a still hold lifts it to be moved (saved as % across the screen and down the app list; default 84 % and 45 %, where the Calculator was); size, colour, opacity; the edge is left to Back unless "The edge pulls the letters too"; each turn is logged ("letter circle: turned from A to M"). Settings in Letter index with a live preview. D150. |
| 4a | Lags and problems in the logs | Partly | Numbers above. Redirect: MagicOS draws its home screen at 90 % while going home ([54,119][1026,2269] on 1080 × 2388 in about 40 redirects; recents always full size) and shrinks the window with it, so the window comparison caught 6. The probe now compares with the screen (`RecentsDetector.shrunkInScreen`, tested with his numbers), so those redirects skip the 260 ms wait. D152. Not done: the 300–430 ms first frame on return. |
| 4b | Icon colours lag, or are wrong for a while (wallpaper and layout changes) | Done, to check on the phone | His icons use "System colours"; MagicOS reported its colours 7–12 s after each change and the old HSL guess looked different. The guess is now Material You's tonal spot (accent 1 at chroma 36, neutral 1 at chroma 6; tones 90, 30, 20) with CAM16/HCT ported from material-color-utilities (`MonetTones`, checked against its reference values); the hold waits 45 s; a late change fades over 0.6 s; the log compares the guess with what came. D153. |
| 5 | Wallpaper library: no animation after placing | Done | Picking a picture plays the wallpaper transition from the tapped thumbnail (the screen previews the picture behind the sheet, so that is when it changes); Apply only closes the sheet. D154. |
| 6 | Wallpaper sheet too small | Done | Two rows and a bit by default; a handle pulls it to about three and a half rows, remembered; "Blur and dim" is no longer cut. |

**Known bugs and limits**
- The lag work is not measured on the phone: the hub, the letter drag and the circle want a new debug log.
- The 300–430 ms frames when the launcher comes back to the front, and the 100–200 ms ones on the first drawer
  opening: cause not found (the drawer is already kept composed while closed); the screen-named lines will show it.
- The going-home check by the screen cannot be tried on the emulator (its launcher does not shrink the window); a
  recents swipe taken for going home would be undone by "Reopen recents".
- MagicOS may mix its colours differently from Android's tonal spot; the log line says how far off.
- With "Both edges", the circle always brings the letters out on the right edge.
- Still open from before: SINK does not dip the neighbouring buttons; picking apps only from the drawer.

**Questions for Med**
1. The circle: 120 dp, 85 % opaque at rest, 25 % while the apps scroll. Right on your phone, or bigger, fainter?
2. One full turn for A to Z (Letter index › Behaviour › "Circle: turn for A to Z"): too quick or too slow?
3. While you turn it, the letters come out at the right edge. With "Both edges", should they come out on the side
   nearer the circle?
4. After a wallpaper or layout change your debug log will say "the system's colours came … s after the change: …
   the launcher's guess was … (off by …)". Over 2 means MagicOS mixes its colours another way; send me that line.
5. The ripple now plays when you tap a picture in the wallpaper screen. Is that the moment you meant, or after Apply?
6. If a recents swipe ever lands on the launcher instead of recents, tell me ("Reopen recents" should bring it back).

**Exact next step:** read Med's next debug log: the "Frames:" lines now name the screen ("study hub", "drawer",
"letter circle", "wallpaper screen"); "letter circle: turned from … to …"; "the system's colours came …"; and
whether redirects now mostly say "its going-home animation is running, redirect in 0 ms". If the hub or the letter
drag is still late on more than 5 % of frames, get a trace from the phone (a debug build with method tracing, or a
Perfetto trace from Med) instead of guessing; then the first frame on return.

## Session of 2026-11-20, evening: 0.11.1, Med's corrections to 0.11.0

Brief: Med's message on 0.11.0 in the chat (a debug log, and a picture of the drawer with the letters frozen, bent,
while he scrolled with them). Two things I had misunderstood: the circle is not a knob to turn but an area, where any
swipe that starts acts as if he had started the letter scroll with his finger there, following it; a tap on an app
inside still opens it; its opacity, size and place are set in Settings, not by holding and dragging; as many areas as
he wants, each with left or right and where it begins; and an option for the letters to come only from the areas.
And in the wallpaper library the transition should happen on Apply, not when a picture is tapped. Also: lag when
scrolling with the letters, and every popup and appearing transition smoother and more fluid. His answers to my two
questions: areas "edge + place" (left or right is the edge the letters come out of; where it begins is the area's
place across and down, and its size, all by sliders in Settings); wallpaper "change on Apply" (a tap only picks the
picture, seen in the sheet and full screen only while placing; the screen keeps its wallpaper; Apply ripples from the
old wallpaper to the new one, from the Apply button). Branch `ccr-f4d083cc-vpyudk`.

**Released:** 0.11.1, by the CI run of the commit "0.11.1 [release]: …" that carries this entry. CI before it:
run 95 (the letter areas, the glide and the frame split) passed every check, the new one
included: a slow swipe from the default area drove the letters ("letter area: swiped from S to Y"), and the
"Frames:" lines carried their split (on the emulator, which draws in software, the numbers mean little). Run 96
(the wallpaper on Apply and the transitions) passed every check; a still screen drew 2 frames in 3 s at home, 0 in
the drawer, 6 in search (the field's cursor), 0 in the wallpaper screen and 0 in the study hub.

**His 0.11.0 log:** the launcher's icon colour guess was off by 0.0 to 1.8 from MagicOS's colours (the tonal spot
matches); redirects mostly said "redirect in 0 ms" (the going-home check against the screen works); frames: the
letter circle 13–45 % late, the string 5–37 %, the wallpaper screen 39–61 % with the GPU at 25–35 ms.

**Items**

| # | Med's point | State | What |
|---|---|---|---|
| 1 | The circle is an area: a swipe from it is the finger on the letters; a tap on an app in it still opens the app | Done | `LetterAreaViews.kt`: the drawer's outer box reads every touch first (Initial pass); one that starts inside an area and moves past the touch slop before the long-press timeout is consumed and drives the string through `IndexDrive.window` (the finger in window coordinates, turned into the string's own by `ScrollIndex`), so the bend, bubble, highlight, haptics and glide are the finger's. A tap or a hold stays with the app (the icons' own gesture uses the same timeout and slop, and reads after). The log says "letter area: swiped from C to M". D156. |
| 2 | Size, place and opacity from Settings; as many areas as he wants, left or right, where each begins | Done | Letter index › Layout › "Letter areas": per area the edge (Left edge / Right edge), Across, Down the apps (%), Size (48–240 dp), Remove; "Add an area" puts the next one on the other side, lower; Reset. Colour, opacity (0 %: invisible, still working) and opacity while the apps scroll on the Look tab. The settings preview's pretend thumb swipes from the first area, twice, then rests. Stored as "right,84,45,120" per area; a circle he had moved in 0.11.0 becomes the first area where it was. |
| 3 | An option for the letters to work only from the areas | Done | "Letters only from the areas", on by default: the edge strips are off, so Back works along the whole edge; off, the edge pulls the letters too (0.11.0's "The edge pulls the letters too" carries over). |
| 4 | Lag: the letters freeze while scrolling with them | Done, to check on the phone | 0.10.4's jump (more than 3 rows away: jump, then glide) built a whole screen of rows in one frame; each letter change could do it again. The glide now moves the list at most 100 rows a second (A to Z in about half a second), and up to 6 rows away it scrolls by pixels, so the grid builds the next row ahead between frames. "Frames:" lines now split the time into input and composing, layout, draw and sync, next to the GPU. D157, D161. |
| 5 | Wallpaper library: the transition on Apply, not on tap | Done | A tap rings the picture (a check pops in, the picture settles in a little); the screen keeps its wallpaper, colours and look; the picture shows full screen only while placed, then fades back. Apply: a picture of the screen, sheet and all, ripples away from the Apply button over the new wallpaper, its icon colours and looks; the files and the system wallpaper are written after. While fixing it: "This picture" started empty, so Apply without a new picture wiped the picture's own look; it now starts as that look and is written only when edited. D158, D159. |
| 6 | Every popup and appearing transition smoother | Done | Overlays leave the way they came: sheets sink back, a folder and the speed bump card shrink to where they grew from, the "Customise" chip to the finger. The wallpaper screen, its placing bar and "Show controls", the bar while picking apps, the bulk tag sheet and the chip went in or out in one frame; they now go through `Presence`. The drawer's tag list and the wallpaper sheet no longer recompose on every frame of their motion (the tag list rebuilt the whole drawer). D160. |

**Known bugs and limits**
- Not measured on the phone yet: the glide's cap and pixel scrolling, and the areas themselves. The next log's
  "Frames:" lines say whether late drawer frames are composing and layout (building rows) or drawing and the GPU.
- The areas work with the String letters only; with the Rail, the rail is used as before.
- The wallpaper screen's GPU time (25–35 ms in his log) is its glass sheet over the live home screen; only its
  recomposition was cut here.
- Still open from before: the 300–430 ms first frame when the launcher comes back to the front; SINK does not dip the
  neighbouring buttons; picking apps only from the drawer.

**Questions for Med**
1. The first area: right edge, 84 % across, 45 % down the apps, 120 dp, 60 % opaque (25 % while the apps scroll).
   Right on your phone, or another place or size?
2. "Add an area" puts the next one on the other edge, 20 % lower. Is that a useful start?
3. After you place a picture, it fades back to your current wallpaper until Apply. Would you rather it stayed on
   screen (still without the transition) until Apply or Cancel?
4. Scroll with the letters for a while and send the debug log: the "Frames:" lines now say where the time goes.

**Exact next step:** read Med's next debug log: "letter area: swiped from … to …" lines, and the "Frames:" lines for
"drawer; letter area" with their split (input and composing, layout, draw, sync, GPU). If the drawer is still late on
more than 5 % of frames while the letters move, look at what the split points to (composing: lighter app cells;
draw or GPU: the glass over the list) before changing anything else.

## Session of 2026-11-20, night: 0.11.2, Med's answers on 0.11.1

Brief: Med's message on 0.11.1 (a debug log from 19:10 to 21:01 and his settings export): "I told you to make things
snappier and smooth and faster, now almost all the animations lag", but the letter areas work very well; the search
pill still goes higher than the keyboard and comes back, more often after a wallpaper change; with the pill on liquid
the animations are snappier, so make that the default and find out whether the frosted glass is the problem; a board
photo in "TD micro" (around 11:00) made the folder in ESSAI but the photo did not save (the camera asks to confirm);
and ideas worth doing, with questions before any change. His answers: the switch was the theme's "Kind of glass:
Liquid"; search "never above"; for the camera, "give me the option to select the camera app I want" (he could not
test, not being in class); first idea "Still glass in motion", and more ideas. Branch `ccr-f4d083cc-vpyudk`.

**Released:** 0.11.2, by the CI run of the commit "0.11.2 [release]: …" that carries this entry. It replaces the APK
that run 99 had published one push early (commit `d8e1574`, "Faster CI: …"): that commit's message described the new CI
rules and so quoted the release marker, and the release step reads the whole message (my mistake; the rule in
NEXT_SESSION_PROMPT.md now says the body counts too). Run 99 had passed every check before publishing; the app's code
did not change after it, only CI and documents. Run 98 (`b09caf1`) had failed one check, "What's new" (the version said
0.11.2 before its CHANGELOG entry, which came with the next push).

**His 0.11.1 log:** the launcher's own work per frame was 0.2–4 ms (input and composing, layout, draw); almost all of
each frame was preparing the drawing for the GPU ("sync": mostly 7–20 ms, 48 ms in the study hub) and the GPU itself
(6–26 ms): home at rest 20–35 ms a frame, the drawer 30–39 ms, the letter areas 30–94 ms, the wallpaper screen 41–58
ms (GPU 18–26), the study hub 142 ms. The numbers matched 0.11.0's except the hub; 0.11.1 had made more things move
(sheets sinking back as they closed), so the same cost showed in more places. At rest the home screen drew 600 frames
a minute: the unmarked block's halo breathing every 14 s. Letter areas: many swipes ("swiped from U to S" and the
like), no complaint. Icon colours against MagicOS's: off by under 1.3 three times, 2.9–3.7 twice and 7.4–7.6 twice
(two pictures MagicOS mixes differently). Redirects fine. About half the search openings had no keyboard 200 ms after
asking.

**Items**

| # | Med's point | State | What |
|---|---|---|---|
| 1 | Animations lag | Partly, to measure | Closing is a 110 ms fade again (no sinking glass); outline paths of themed buttons, pills and materials are kept between frames (`OutlineCache`), the likely cause of the hub's 48 ms of sync; the halo rests 5 minutes; "Frames:" lines give the refresh rate. D165. |
| 2 | Liquid snappier: make it the default; is frosted the problem? | Done | The default theme's glass kind is Liquid (D164). In the code Frosted and Liquid take the same steps (same blur, same AGSL shader; liquid bends the edge), so the difference he felt is measured by the speed check on his phone. |
| 3 | Search field above the keyboard | Done | `KeyboardLine`: the field waits at the lowest of the last six opening heights and only the keyboard pushes it up; every opening is logged with where the field waited; no second request while the keyboard slides in. D162. |
| 4 | Board photo not saved; choose the camera | Done, to try on the phone | The camera writes into a private file (FileProvider) and the launcher saves it in Pictures/ESSAI/<class>; the photo in progress survives a launcher restart; kept whenever written. Settings → Study companion → More: the camera app, and a test photo into ESSAI/Test. D163. |
| 5 | Ideas, first "Still glass in motion" | Measured first | Settings → Diagnostics → Speed check plays the drawer, search, the home menu and the hub with the glass as set, without blur (what still glass would save), frosted, liquid, without shadows, without the material and as a tint; one "Frames:" line per step. The glass library redraws a moving surface every frame, so still glass needs a picture of the surface (taken on the main thread) or a pre-blurred backdrop (wallpaper only); the numbers decide. D166. |

**CI speed-up (Med's change of plan, D167)**

Where the time went before (run 97, the last green run, 16m18s from push to release): the build job 5m16s, of which
5m07s compiling and testing with no Gradle cache (a branch only read one, and main had none); the emulator job 10m43s,
of which 10m31s in one step that downloaded the system image, cold-booted Android and ran `ci/smoke.sh` with 142 fixed
sleeps (254 s); publishing 12 s. Changes: the system image and a booted emulator's snapshot are cached (on a miss one
step boots and saves it, the test boots from it and does not save it back); Gradle saves its cache on every branch;
the emulator test runs only for the release marker, for `[smoke]`, for manual runs and for changes to the redirect,
`ci/` or the workflows; `smoke.sh` waits for what it checks (with a timeout) instead of fixed sleeps, keeps the
warm-up, the ANR handling and every check, and now ends with the time of each section.

| Step | Run 97, before | Run 103, fills the snapshot | Run 104, after |
|---|---|---|---|
| Gradle setup (restores its cache) | 0m02s | 0m06s | 0m06s |
| Unit tests and release APK | 5m07s | 2m23s | 2m18s |
| Gradle cache saved (post step) | 0m01s | 0m19s | 0m16s |
| **Build job** | **5m16s** | **2m54s** | **2m46s** |
| System image and snapshot caches | none | 0m17s (image restored, snapshot missed) | 0m54s (both restored) |
| Boot, wait for calm, save the snapshot | none | 2m35s (calm after 89 s) | skipped |
| Run on emulator (boot, `smoke.sh`) | 10m31s | 11m06s (`smoke.sh` 646 s) | 10m27s (`smoke.sh` 592 s, calm after 6 s) |
| **Emulator job** | **10m43s** | **14m22s** | **11m32s** |
| **Push to result** | **16m18s** (with publishing) | **17m21s** | **14m22s** |

A push that changes none of the redirect, `ci/` or the workflows now ends after the build job: about 3 minutes instead
of 16. Runs 98 and later also run the speed check (section 17, about 65 s), which run 97 did not have.

How it got there: run 99 filled the first caches (and published 0.11.2, above); run 100 measured them: the build job
went from 5m16s to 2m20s, but the emulator step took 12m08s. Run 101 failed checks 2 and 3: the first snapshot had
been saved the moment Android said "booted", in the middle of its first-boot work, so every run started inside it, and
the launcher was killed twice as "not responding" in its first minute (an input event waited 5 s with the CPU 87 %
busy), taking its redirect service with it. `ci/settle.sh` now waits for a calm phone (the CPU under 40 % busy, read
from `/proc/stat`, three times in a row; the CPU pressure file cannot be read from adb, which run 102 showed): before
the snapshot is saved (run 103: 100 % busy for over a minute, calm after 89 s) and at the start of `smoke.sh` (calm
after 12 s from the restored snapshot); the first redirect checks then passed without a retry. `smoke.sh` now ends with
each section's time and its slow waits: most of its ~11 minutes is its own work (72 screen dumps, 29 screenshots, the
speed check, and the sleeps that check something does not happen, which stay). Two waits on a moving screen were slower
than the fixed sleeps they replaced (a dump of a moving screen waits ~10 s and fails); the glass benchmark keeps its
fixed wait for that reason.

**The snapshot: kept.** Med's stopping rule gave the calm snapshot (v3) one run: any "not responding" or failed check
and it would be dropped for a cold boot each run (the system image cache kept, then `settle.sh`, then the test). Run
104 restored it: calm after 6 s, no "ANR in" line in the system log (20,301 lines, from the install to the end), the
launcher one process from start to end, no retry, every check passed; `smoke.sh` 592 s. If a run shows "not
responding" again, the cold boot is the fallback: remove the two snapshot steps and `force-avd-creation: false` from
the workflow (the system image cache and the calm wait at the start of `smoke.sh` stay).

**Known bugs and limits**
- CI: a push that changes the app but not the redirect, `ci/` or the workflows no longer runs the emulator test; put
  `[smoke]` in the message to run it (a release always runs it).
- Not measured on the phone: whether the fade exits, the outline cache and Liquid make the animations feel snappy
  enough; the speed check will say what costs what.
- Still glass in motion is not built yet (D166).
- The first search opening on a new phone waits at 30 % of the screen; after that, at the lowest opening.
- Ideas offered and not picked yet: 90 Hz during animations, predictive Back, grab any sheet, a focus timer and the
  next class in the pill, now playing, search for settings/contacts/events, quick toggles in the home menu.

**Questions for Med**
1. Run Settings → Diagnostics → Speed check once (about a minute, hands off) and send the debug log: it says what the
   blur, the bending, the shadows and the material each cost on your phone, and whether still glass is worth it.
2. Take a test photo (Settings → Study companion → More) with the camera you want; does it land in Pictures/ESSAI/Test?
3. Does the search field still ever go above the keyboard? The log now says where it waited each time.

**Exact next step:** read the speed check's "Frames: … speed check: <what>, <variant>" lines. If "glass without its
blur" is much cheaper than "glass as set" for the drawer and search, build still glass for the pill and the field
(a pre-blurred backdrop while they move); if "without shadows" or "without the theme's material" is the big saving,
cut those first. Then the hub's line against 0.11.1's 142 ms.

## Session of 2026-11-21 (Opus 5.5): 0.11.3 and 0.12.0, Med's feedback on 0.11.2

Brief: `docs/NEXT_SESSION_PROMPT.md` (Med's feedback on 0.11.2, his debug log of 08:06 to 12:49 and a screenshot of a
flashcard whose formulas showed as plain text), with his answers: search "Keyboard slides with the pill"; keyboard: all
four ideas; flashcards "a small deck with repeats"; maths "Claude writes LaTeX, rendered". Branch
`claude/affectionate-sagan-u5it39`.

**Released:** 0.11.3 (items 1 to 3) by run 110, build 110, after every check passed; 0.12.0 (items 5 to 7) by the CI
run of the commit "0.12.0 [release]: …" that carries this entry.

**Items**

| # | Med's point | State | What |
|---|---|---|---|
| 1 | The clock wrong for some seconds after unlock | Done | One time source, `WallClock` (core/common), set from the wall clock by the minute tick, the time or zone being set, screen on, unlock, start and resume (`ClockRefresher`); the minute delay is only a backup. A "Clock:" line says how long a shown minute had been wrong and what caught it. Unit tests for the timing. D168. |
| 2 | The keyboard slides with the pill; the pill's size | Done; on the phone, to see | The field rides on the keyboard (never above it). On the first frame search starts opening, the field takes focus without the keyboard and `controlWindowInsetsAnimation(ime)` is asked for; the keyboard then moves in step with search (a late one catches up in 180 ms), and is handed back fully up. Refused, taken back or not ready in 1.5 s: the usual way, the field riding on it. Results are laid out above the planned field line. Every opening is logged with its path. Emulator (run 108): "ready 385 ms after asking at the swipe's start … keyboard opened at 883 px (slid in with the pill)". Settings: the pill's height, width and gap above the keyboard, in the Search page's preview. D169. |
| 3 | Recents: the launcher and HONOR's home fight | Done (the hand-over); waits kept | Up to 0.11.2 every return hid the content on the first frame and scaled it in, even when the system had just shown the launcher's last picture with the content (a blink), and with no picture the wallpaper cut in. Now fading in (the default) fades the whole screen, wallpaper included, over the system's home (the window shows the system wallpaper behind it) and never asks for the picture; "At once" uses the picture when up to date and nothing re-animates. Leaving with only a keyboard on its way out lays the screen out without it, so the picture stays usable. "Try the arrival" on the Redirect page. The 80 ms wait after leaving recents is what keeps a redirect off an app opened from a recents card, which no CI check exercises, so it stayed; the 260 ms case in his log was recents opened over the launcher itself (MagicOS reports the launcher's live tile as the active window, so the engine never saw recents open), kept too. The REDIRECT line now says which arrival was used. D170. |
| 4 | Pending from 0.11.2 | Not attached | No speed check log, test photo or search log came with this brief: still glass waits (D166); asked again below. |
| 5 | WM Keyboard | Done; on the phone, to try | Checked against its docs first. Search-friendly typing (no autocorrection or automatic capital, a Search key; Ask Claude mode: text with Send). Settings › Drawer and search › Keyboard: the layout while searching (its automation intents: LIST_LAYOUTS gives the layout in use, SET_LAYOUT switches, the old one comes back when search closes; each answer in the log); a keyboard theme in the launcher's colours (`KeyboardTheme`: the wallpaper-following colours written out, the wallpaper as the keyboard's background if wanted), previewed as a small keyboard, opened in WM Keyboard (it imports a .wmtheme.json opened with it, after its own confirm dialog), saved or shared; the Liquid plugin (Python, API 5, `share` permission: WM Keyboard's plugins cannot start an app, so it hands the text to "Search in Liquid" through the share sheet), installed from Settings. "Liquid" in the text selection menu, "Search in Liquid" in the share sheet and `dev.liquid.launcher.action.SEARCH` open search with text (or ready to ask Claude). D171, D172. |
| 6 | Flashcards: a small deck with repeats | Done | `flashcards` in launcher-data (the single `flashcard` reads as a deck of one) join a local deck (`flashcards.json`), never lost; wrong: tomorrow; right: 1, 3, 7, 14, 30 days. Search shows the card waiting longest, how many more, then "All done for today". Every wrong answer still writes its Note. 0.11.2's answered card moved into the deck. docs/05 updated, with the text to paste (study plan doc, assistant's instructions). D173. |
| 7 | Maths: Claude writes LaTeX, the launcher draws it | Done | A small renderer of the subset (D174) rather than JLaTeXMath for Android (no release since 2020, Android views, fonts in the APK). Rendered offline here with his measure theory cards before shipping (italic corrections for f and superscripts, drawn accents, \xrightarrow with its label); the emulator check (14e) turns a card with formulas over and answers it. |

**CI runs:** 107 clock (build and tests green; its emulator test was cancelled by the next push), 108 search drive
(green with the emulator test), 109 the redirect (cancelled by the release), 110 0.11.3 (green, published), 111 WM
Keyboard (green), 112 the deck and formulas: every check passed but the new section 14e, which failed on its own
set-up (`content insert --bind` splits a value at its colons, so the JSON's are escaped as `\:`; and Settings came
back on the page section 14d had left open, so it is started afresh); 113, the first "0.12.0 [release]" commit, failed
the same section only, and 114 too (nothing was published): Android 14's `content` also halves the backslashes of a
value, so the stored description was not JSON (the formulas' `\\ge` became `\ge`). The test now writes
launcher-data as the HTML Google Calendar's web page makes, colons as `&#58;` and backslashes doubled, and prints what
was stored when the check fails; the launcher now decodes numeric character references in HTML descriptions (a real
gap: the web page writes some characters that way). No "ANR in" was seen in these runs; the snapshot cache stays.

**Known bugs and limits**
- Fixed in 0.12.0, present in 0.11.3: the "keyboard on its way out" layout (D170) also applied when the launcher was
  paused under recents or a dialog with search's keyboard up, so the field dropped under the keyboard until the launcher
  came back; it now applies only when an app was just started from the launcher.
- Whether MagicOS and WM Keyboard let the launcher move the keyboard is only known from the phone's log; the usual way
  is the fallback.
- Fading in, the launcher is fully in about 180 ms after its first frame (about 380 ms after the trigger); "At once" is
  faster, with a cut. If HONOR's home has icons, they still disappear at once.
- The recents waits were not shortened (see item 3).
- The flashcard deck lives in the launcher's files (`flashcards.json`) and goes into its backups (Settings › Backup);
  a restore brings it back with its progress.
- Maths: no matrices or aligned equations (their cells show in one row), and the formulas use the phone's serif face.
- WM Keyboard: the layout switch needs its "Let other apps control the keyboard" switch; the plugin needs "Allow
  plugins"; fonts are not embedded in an exported theme (WM Keyboard's own rule).

**Questions for Med**
1. Speed check (Settings › Diagnostics › Speed check, about a minute, hands off): send the debug log, for still glass.
2. The test photo (Settings › Study companion › More) and a search log are still owed.
3. Search: does the keyboard now slide up with the pill? The log says "the keyboard slides with the pill: ready … ms"
   or "shown the usual way (why)" for each opening; send a log with a few openings.
4. Recents: does the fade over HONOR's home read as one movement, or do you prefer "At once" (Settings › Redirect ›
   Look)? Is HONOR's own home screen empty?
5. WM Keyboard: with "Let other apps control the keyboard" on, does the layout switch while searching? Does "Open in
   WM Keyboard" import the theme, and does the Liquid plugin install?
6. Flashcards: paste the two texts from docs/05 ("Text for Med to paste") into the study plan doc and the assistant's
   instructions; does the evening check-in write `flashcards` with LaTeX?

**Exact next step:** read Med's next log. (1) Each search opening's "search: the keyboard slides with the pill: ready N
ms" or "shown the usual way (reason)": if MagicOS refuses, the reason says whether to ask later or differently. (2) The
"REDIRECT (…); fades in over 180 ms" and "launcher drawn …" lines after recents. (3) The speed check lines for still
glass (the 0.11.2 entry's exact next step).

## Session of 2026-11-21, night (Opus 5.5): 0.13.0, Med's feedback on 0.12.0 and three new ideas

Brief: Med's message after 0.12.0, with his settings export, his WM Keyboard theme file ("wallpaper"), his log of 20:11
to 20:35 and the diagnostics; then his answers to the designer's read I gave him, and three new ideas from my list he
asked for ("Live pill", "Flashcards one tap away", "Search everything"). Same branch.

**Released:** 0.13.0 by run 121 (build 121), the second "0.13.0 [release]" commit, after every check passed; the
first release run (120) published nothing (see the CI runs).

**Items**

| # | Med's point | State | What |
|---|---|---|---|
| 1 | The test photo works but he could not choose the camera | Done | Every camera app is listed (also those that cannot hand a photo back); those open as they are and the photo picker follows, saving into Pictures/ESSAI/<class> (D177). |
| 2 | Search: a fast swipe took the pill down then up with the keyboard; slow, it dipped and the bounce and stretch were not smooth; he wants it up with the keyboard at once, iOS-smooth, speed in Settings | Done; on the phone, to see | The pill only rises, from rest to just above the keyboard, on search's spring; the keyboard rises under it from the first frame, its picture standing in until it is ready; closing takes it down with the pill; the stretch follows the pill's speed and settles without a wobble at Bounce 0 (D175). Speed and Bounce were already in Settings › Search › Behaviour. |
| 3 | Glass in motion: frosted while moving? | No change | His answer was no: the glass stays liquid while it moves. |
| 4 | The keyboard theme: blurry toolbar; his file as the base; follow the wallpaper | Done | Built on his file, no picture inside (crisp toolbar), "Follow wallpaper colors" on, another file can be the base (D177). |
| 5 | The plugin and the layout switch did nothing for him | Replaced | Both removed; a guide to WM Keyboard's Modes (a mode bound to the launcher) instead (D177). |
| 6 | Smoother transitions, popups and closing animations | Done | Overlays leave as a picture of themselves into what opened them or sink, sheets close by drag and by predictive Back (D176). |
| 7 | Live pill | Done | `PillSlot.Live`, in the default items: focus timer, next class, what's playing, cards due; the pill swells when it changes (D178). |
| 8 | Flashcards one tap away | Done | A count on the live item; a tap or a long press opens the quick review over the home screen (D178). |
| 9 | Search everything | Done | People, events, notes, flashcards and options of Settings (D179). |
| 10 | Glass cheaper, a steady 60 Hz (I offered it) | Not done | It needs his speed check log (Settings › Diagnostics › Speed check): without numbers from his phone it would be guesswork (D166). |

**CI runs:** 116 (the search rise; cancelled by the next push), 117 (transitions) and 118 (keyboard theme, camera) green
with build and unit tests, 119 (`[smoke]`: live pill, quick review, search everything, with the new emulator sections
14f and 14g; its build and unit tests passed, its emulator test was cancelled by the release push, which runs it
again). 120, the first "0.13.0 [release]" commit, failed four checks and published nothing: 14f passed (the count on
the pill, the quick review by a long press, Back), but two swipes (14e, 14g) landed while the launcher's window was
still coming back after Home (InputDispatcher: "No new touched window"; the system's gesture service got them), so
search never opened; the test now waits for the redirect's "launcher drawn" line and the end of the transition
(`home_and_settle`). And an option found by search opened the Settings page 14e had left in the background, a real
bug: with `NEW_TASK` alone Android brings that task back as it was; every link to a page of Settings now clears it
(also Customise, gestures and the drawer's menu, which had the same flaw). 121, the second release run: every check
passed, published. Its 14e also showed that Back with the keyboard up still only puts the keyboard away (search stays
open) with predictive Back switched on (D176): the keyboard's back callback is registered after the activity's. Unit tests: 273 (5 new for the finder: matching, cards, the Settings links). No "ANR in" seen; the snapshot
cache stays.

**Known bugs and limits**
- Nothing of this entry has been on Med's phone yet: the emulator checks the pill's count, the quick review and search
  finding a setting and a card, not how the motion feels.
- The keyboard's picture is taken through the redirect service's screenshot ability (Android 11+); a service enabled
  before 0.13.0 may need to be switched off and on once. Until a picture exists, a plain plate stands in.
- What's playing needs notification access, and only shows players that publish a media session (most do).
- The pill's swell redraws its glass for about a second each time the live item changes (not at rest).
- Search everything reads events from a month back to six months ahead, notes from six months back; contacts only
  after he allows them from the row search offers (once).

**Questions for Med**
1. Search: does the pill now go up with the keyboard in one motion, fast and slow? Is Speed/Bounce enough to tune it?
2. Keyboard theme: import it once (Settings › Drawer and search › Keyboard › Look › Open in WM Keyboard); is the
   toolbar sharp, and does it follow the next wallpaper by itself?
3. Live pill: is the order right (focus, a class within 15 minutes, music, the block running, the next one, cards)?
   Notification access for the music part: the Pill page has the button.
4. Quick review: is closing by itself after the last card right, or should it stay open?
5. Search everything: did you allow contacts? Anything it should find that it does not?
6. Still owed: the speed check log, for a cheaper glass at a steady 60 Hz.

**Exact next step:** read Med's next log: "search: keyboard opened at … (…)" for each opening, "search: the keyboard's
picture was taken (…)", "Live: media sessions …" (only when not allowed), and his answers above; then, with the speed
check log, measure the glass (D166) before changing it.

## Session of 2026-11-22 (Opus 5.5): 0.13.1, Med's questions on 0.13.0

Brief (in chat): two screenshots (the home screen with the live pill, search with the keyboard), his settings export,
diagnostics and a log of 00:41 to 11:09. He asked what the live pill does (a tap opened the study hub); reported that
after a wallpaper change the search pill goes too high the first time; asked again for a switch so the keyboard's
background changes with the wallpaper; asked to reimagine the line under the clock; and asked for recommendations.
Branch `claude/confident-planck-nkjqga`, started from `claude/affectionate-sagan-u5it39` (0.13.0; `main` was at 0.11.2).

**Released:** 0.13.1 by the CI run of the commit "0.13.1 [release]: …" that carries this entry.

**Items**

| # | Med's point | State | What |
|---|---|---|---|
| 1 | What the live pill does; a tap opened the study hub | Explained in chat | By design (D178): a tap on the live item opens Today unless it shows music (the player) or cards (the quick review). His pill: search, live, study circle; with the study item on the pill, the live item leaves blocks and classes to it and shows the focus timer, music and cards. |
| 2 | After a wallpaper change the search pill goes too high the first time | Done | His log: "it goes to 1188 px", then "keyboard went down to 933 px while open", the field at 1206 px. D182. |
| 3 | A switch: the keyboard's background changes with the wallpaper | Done; needs his three switches in WM Keyboard | The launcher hands WM Keyboard the picture (Android 13+ does not let WM Keyboard read it) through two themes it switches between. D180. |
| 4 | Reimagine the line under the clock | Done | Never the pill's countdown: the block's next step with a ✓, the class now (until when, where), a class soon, "free until", and a second line for the rest of the day. D181. |
| 5 | Recommendations | Given in chat; two speed fixes done | D183: the hub was redrawn every second under a focus; the board picture was blurred on every frame. The rest below. |

**Recommendations given (not built; his choice)**
- Speed: the focus countdown redraws the home screen's glass every second for as long as a focus runs (his idle home
  logged 600 frames, 35 % late, around 10:04). An option "seconds only in the last minute" would cut it to once a minute.
- Speed: the drawer's letter area runs 40 to 56 % late (composition 4 to 11 ms a frame, sync 15 to 18 ms): to measure
  and make draw-only.
- Speed: the speed check log (Settings › Diagnostics › Speed check) is still owed, for a cheaper glass at a steady rate.
- Battery: a calendar sync is requested at most every 5 minutes on coming home; every 15 minutes would do.
- Design: with the line no longer repeating it, the study circle and the live item on the pill overlap less; the
  circle could become the live item's ring.
- Practicality: a long press on the line (today: the clock widget's edit) for Start focus, Ask Claude about the block,
  Write a note.

**Known limits**
- The keyboard's wallpaper needs, once: WM Keyboard's Photos permission ("Allow all"), "Let other apps control the
  keyboard" (with "Change the theme" in its Allowed actions), and both themes added. Changing the Keyboard page's theme
  (its base, the launcher's keys) means adding the two themes again; the look (search or sharp) does not.
- Only wallpapers the launcher shows are handed over: one set in another app makes the launcher draw the system's
  wallpaper instead, and the keyboard keeps its last picture until the launcher shows one of its own again.
- The line's class facts read the class calendars (the account's main calendar unless set); a class without a place
  shows no room.
- Nothing here was seen on his phone: the emulator checks the line's old cases (14b) and the keyboard's drive.

**Questions for Med**
1. Keyboard: after the three steps, does "Try it now" answer "Done", and does the next wallpaper change the keyboard by
   itself? Is "Like behind search" right, or "Sharp"? Is the toolbar sharp enough on the picture?
2. The line under the clock: while you focus, is the next step useful, and the ✓? Is the second line too much?
3. Search: after a wallpaper change, does the pill stay at the keyboard now (the log says "the pill comes down onto the
   keyboard" when it had to correct)?
4. Which of the recommendations do you want?
5. Still owed: the speed check log.

**Exact next step:** read Med's next log: "Keyboard: wallpaper behind the keys (…)" lines (each hand-over and WM
Keyboard's answer); "search: the keyboard rises with the pill: … taller than the … px it opens at" and "the pill comes
down onto the keyboard" after a wallpaper change; the "study hub" Frames lines (fewer frames while it stays open, and
their time).

## Same session, afternoon: 0.14.0, Today as a study kick-off

Brief (in chat, after 0.13.1): two screenshots of Today (timeline, blocks, tasks, sync row, six chips), his check-in
instructions and a new settings export. He wants the check-in simple and fun ("what would you like to study today",
considering what he studied in class; a button that takes him to Claude with that context; Claude asks questions to
see his progress and for today's lesson; then they study), no study blocks in the calendar, no more absence questions,
recommended tasks, the note for tonight and Quiz me kept, everything else gone; "ask me before you do anything".

**Asked first** (four questions): the Today screen (he chose the kick-off screen), where recommended tasks come from
(Claude + automatic), absences (keep "present?" on the phone, show them as numbers only: "I don't know where to see
them"), a timer with "Study with Claude" (yes, 45 minutes). Built as answered: D184 to D186.

**Released:** 0.14.0 by the CI run of the commit "0.14.0 [release]: …" that carries this entry.

**For Med to do once:** paste `docs/checkin-prompt.md` where his old check-in instructions are (his plan doc's
section 9), and set Settings › Study companion › More › Check-in message to "Study check-in. Read section 9 of my plan
doc https://claude.ai/artifact/GWFvdSR6uGVM7sVL1RT9JB and follow it." (his current one still says "Evening check-in").

**Known limits**
- The old system's blocks already in his calendar still show (line, pill) until they pass, or until he lets the new
  check-in delete them (it asks once).
- The pill's study circle fills with blocks done: without blocks it stays empty; it still opens Today.
- Recommended tasks from Claude appear once a check-in writes `recommended` into launcher-data.

**Questions for Med**
1. Today: is it calm enough now? Anything you miss from before?
2. Does Claude's first message feel right (short, your classes, one question at a time)?
3. The study circle on the pill counts blocks, which are gone: turn it into minutes studied today, or remove it?

**Exact next step:** his next log: "Study: session: …" lines, "ask Claude (… characters)" after Study with Claude, and
the Frames lines of Today ("study hub").

## Session of 2026-11-22, evening (Opus 5.5): 0.15.0, search that lands, Settings after WM Keyboard, the unlock reveal

Brief (in chat): Med's settings export, diagnostics and a log of 21:53 to 21:57. "The search page, every time you
don't do it right: it isn't smooth, it lags and the pill sometimes goes to the top"; "the wallpaper doesn't appear in
the keyboard even though I turned the setting on"; "copy the design and animations of WM Keyboard's settings, don't
delete any feature, add if you can, move settings if you should but tell me"; "when I open my phone, the same animation
as when I change the wallpaper, starting from random places"; "improve animations and the overall experience". Branch
`claude/dreamy-turing-2ef9yy`, moved onto `claude/compassionate-ramanujan-snhabi` (0.14.0 and its CI fixes; `main` was
still at 0.11.2).

**Asked first** (three questions): the unlock reveal starts from "frosted, then clear"; Settings in "wallpaper colours"
(WM Keyboard's layout and motion, Material You); the pill goes to the top "when I open search".

**Items**

| # | Med's point | State | What |
|---|---|---|---|
| 1 | Search not smooth, lags, the pill goes to the top when opening | Done; on the phone, to see | His log: search at 1.13 to 1.15 when the keyboard was ready (Bounce 40 %, flicks), so the pill floated 25 to 30 dp above a keyboard that stops at its height; closings while the opening's drive still held the keyboard asked for a second control ("the system took the keyboard back", the keyboard jumped up). Now the pill and the keyboard stop together and the bounce is a squash of the pill; the keyboard is handed back once fully up; one control per opening, a finger pushing search away takes the keyboard with it; a keyboard taller for a moment is not followed; the closed pill no longer rests 116 dp above a keyboard going down; the page's cards come in one a frame. D188. |
| 2 | The keyboard wallpaper does nothing | Explained, made visible | WM Keyboard answered "no theme is called \"Liquid wallpaper 2\"" at every change: the themes were never added (or one twice). Two themes stay necessary (WM Keyboard's keyboard stops redrawing while hidden and loads a theme's picture once per load; checked in its source). Settings shows which theme is there, a failed hand-over says so on the home screen and opens the page, the theme there gets the picture too. D189. |
| 3 | Settings like WM Keyboard's | Done | Hues per place, tiles, headings with breadcrumbs, grouped cards, page pushes with flying tiles, staggered rows, segmented tabs, chips, expressive sliders, "?" on every option, bold matches in search. Nothing moved or removed. D191. |
| 4 | The unlock reveal | Done; on the phone, to see | Frost up at screen off, cleared from a random point at the unlock in the wallpaper transition's style (Settings › Wallpaper › When you unlock). D190. |
| 5 | Animations and little tricks | Done in the items above | The squash landing, the follow-the-finger keyboard, the staggered search page and settings rows, the varied shaders, the deferred live previews. |

**Known limits**
- None of the drive changes has been on his phone: the log lines are unchanged ("the keyboard rises with the pill: ready
  … ms", "keyboard opened at … (slid in with the pill)"), plus "the keyboard was taller for a moment (… px): the pill
  stayed" and "the keyboard stayed taller (… px): the pill rises onto it".
- The keyboard wallpaper still needs Med to add both themes in WM Keyboard (Settings › Drawer and search › Keyboard ›
  Look, step 3), with WM Keyboard's Photos permission and "Let other apps control the keyboard".
- The unlock reveal needs the launcher in front at the unlock (his "When the screen turns off: home" does that).
- Settings' Theme lab and the glass benchmark keep their own frames (they are drawn by other code).

**Questions for Med**
1. Search: does the pill now land on the keyboard and stay? Is the squash right at Bounce 40 %, or too much?
2. After adding both themes, does "Try it now" answer "Done", and does the next wallpaper reach the keys?
3. The unlock reveal: right length (it uses the transition's 800 ms × 1.35 for Ripple)? Should it have its own length?
4. Settings: anything from WM Keyboard's settings you miss (the settings depth, favourites, recent searches)?

**Exact next step:** read Med's next log: "search: …" lines for each opening and closing (no "the system took the
keyboard back" should appear on closings now), "unlock: the frost leaves (…) from …", and "Keyboard: wallpaper behind the
keys (…)" after he added both themes.

**Released:** 0.15.0 by run 135 (build 135; run 134, the first "0.15.0 [release]" commit, was cancelled by the review
fixes' push), every check passed: the three emulator parts, then the release. 0.15.1 by the run of the commit
"0.15.1 [release]: …" that carries this addendum.

**0.15.1:** run 135's study part showed a first search with no keyboard picture yet (the emulator's first guess, 720 px,
for a keyboard of 883 px): "the field ended at 757 px", then "the keyboard stayed taller (883 px): the pill rises onto it".
The old hand-over ("search open and still") had stayed as a fallback and handed the keyboard back on the frame the rise's
glide to 883 px began, so the system put it at its full height over the pill. Now the keyboard is handed back only once
its share stands still (the glide done), with a second of frames fully up as the limit. On Med's phone the picture's
height matches the keyboard (933 px), so this showed only when the keyboard's size changed.

**0.15.1** published by run 136 (build 136), every check passed. Its study part: the first search with no keyboard
picture still ended with "the field ended at 838 px" (of 920) and "the keyboard stayed taller (883 px): the pill rises
onto it": the rise's glide repeats its start value on its first frame, so the share looked still for two frames and the
keyboard was handed back as the glide began. **0.15.2** (on the branch, not published: Med's limit of two releases a
session) waits for the glide itself to have ended (`keyboardRiseGliding`). Next CI run's study part should say "the field
ended at 920 px" on the first opening and no "stayed taller" line.

**Exact next step:** publish 0.15.2 with the next release; read Med's log for the lines listed above.

## Same session, late: 0.15.2, Med on 0.15.1 (the unlock reveal, the Theme lab)

Med: the settings design "a really good job except a couple of places like the Theme lab, double check"; the unlock
ripple "doesn't feel the same as when I change the wallpaper, and it is very slow, I want it instant". His log of 23:45 to
23:50: seven unlocks, each "unlock: the frost leaves (ripple) …" (1080 ms: 800 ms × 1.35), one followed 95 ms later by the
screen-off redirect's arrival fade; search 30 openings, all "slid in with the pill", the field ending at 950 or 951 px,
one "taller for a moment (1188 px): the pill stayed" after a wallpaper change (D188 working); the keyboard wallpaper
switched "Liquid wallpaper 2" then "1" (both themes added).

| # | Point | State | What |
|---|---|---|---|
| 1 | Unlock reveal: not like the wallpaper change, slow | Done; on the phone, to see | D192: the wallpaper change's pipeline with a sleeping picture of the home screen, at once, 380 ms, no arrival fade on top. |
| 2 | Theme lab (and other places) not in the new look | Done | D193: the kit in core/designsystem; the Theme lab rebuilt from it; the glass panel and the last stock chips and sliders. |
| 3 | 0.15.2's search glide fix | Released with it | See the 0.15.1 addendum. |

**Questions for Med:** is 380 ms right for the unlock (Settings › Wallpaper › Behaviour › Unlock animation length)?
Does the picture asleep look right (darker and paler), or should it be darker still / blurred?

**Exact next step:** his next log: "unlock: the screen wakes (ripple, 380 ms) from …" without "(no picture of it asleep
yet…)" most of the time.

### Addendum: run 137's failed check (14g), a test swipe too short

Run 137 (the glide fix, [smoke]) failed one check, study's 14g "search finds an option of Settings": its swipe down
reached the launcher as "released at 0.40, 710 px/s", under both what opens search (more than 0.40 of the way, or
800 px/s), so search stayed shut and "liquid stretch" was typed into nothing. Not a regression: the glide fix showed
as wanted in the same run (the first search's field ended at 919 px, no "stayed taller"), and run 136 passed 14g with
a swipe that only just opened it ("released at 0.33, 869 px/s"). The emulator's `input swipe` of 250 ms arrives as
a few points and the lift's own move is dropped, as the drawer's swipe did in 0.5.0. So: each release now says what it
does ("…px/s: opens" / ": closes", drawer and search), and the smoke test's search swipes go through `open_search`,
which swipes again, slower (450 ms), when the log says the first left search shut.

### Addendum: run 138 (0.15.2's first release run) failed 14f; D194

All of run 138 passed but study's 14f "the pill shows the card still due": its home screen was search's page, still
open. In 14e the test opens search and presses Back two seconds later to put the keyboard away; this time the launcher
still held the keyboard then (ready at 23:19:27.152, not yet handed back at 29.048: the emulator's frames are slow, the
hand-over waits for the glide to end and two still frames), so the Back made the keyboard hide itself and cancelled the
launcher's control ("the system took the keyboard back while the launcher moved it, 2553 ms after asking"), and the
fallback asked for the keyboard again the usual way: up again at 30.452 ("it started 3656 ms after search opened, the
field then at 870 px", on its way down). The Back closing search at the end of 14e then only hid the keyboard. On the
phone the same happens to a Back pressed in the half second the keyboard rises. D194: a keyboard taken back as it rises,
search open, is left to the system (away: the field comes down, search stays open; up: it stays), logged "search: as
it rose, …"; the rise's glide ends within half a pixel, so the hand-over comes about 170 ms sooner. Version labels of
this release's comments said 0.15.3; they say 0.15.2 (it never got out: run 137 was a smoke run, run 138 failed).

**Exact next step:** run 139 ([release], Med said "try again"): 14e should pass with either "as it rose … put away" or
no such line, 14f and 14g pass, and 0.15.2 is published. Then Med's log: the unlock lines, and no "as it rose" lines
unless he pressed Back during an opening.

Run 139 published 0.15.2 (LiquidLauncher-0.15.2-build139.apk): all three parts passed; study's first search "the field
ended at 920 px" with no "stayed taller"; every release logged ": opens"; no "as it rose" line (the Back came after the
hand-over this time) and no second swipe needed.

## Same session, 2026-11-23: 0.15.3, Med on 0.15.2 (the unlock, several board photos)

Med: the unlock animation "a complete failure: I see my phone open, then after a bit the animation is done; remove it
if you want"; "I want to be able to take multiple pictures when I study from the workspace: I take a photo, it saves
to its right place, then I'm back on the home screen"; recommendations and questions welcome; his diagnostics
(0.15.2 build 139, 09:48 to 17:49). Asked: how to take several photos (he chose the launcher's own camera) and which of
four ideas to build (searching the boards' text, flashcards from a photo, a lecture PDF, study time per class): "none
of the ones you mentioned is good".

His log: no "unlock:" line all day (the reveal never got its moment in front, or it came after the home screen
showed, as he saw); board photos at 10:59 to 11:00 three in 33 s, one tap each, ~1 s between saving and the next
camera; search 60 openings all "slid in with the pill", the field ending at 950/951 px, no "as it rose"; frames: the
speed check's study hub 61-76 % late (sync 34-48 ms, 12 ms with a flat tint), the drawer's letter scrubbing 46-69 %
late (input and composing 8-13 ms, sync 18-19 ms); the clock caught minutes behind after the launcher was frozen in
the background (it corrects itself as it starts).

| # | Point | State | What |
|---|---|---|---|
| 1 | Unlock animation: a failure, remove | Done | D195: removed, settings gone, the shaders as in 0.14. |
| 2 | Several board photos at once | Done; on the phone, to see | D196: the launcher's own camera. |
| 3 | Jank: study hub, drawer letters | Looked into, not changed | Below: no safe fix without a measurement on the phone. |

Jank, read from the code: the frame meter's "sync" is SYNC + COMMAND_ISSUE, the render thread's own work, off-screen
layers included. The hub costs 34-48 ms there with any glass (with or without blur, bending, shadows or the theme's
material) and 12 ms with a flat tint; the drawer's and search's glass cost 12-15 ms. What the hub's glass has and theirs
has not: it grows out of the pill (its size and corners change every frame), it exports its glass as a layer for the
controls on it (`sheetSurface(..., exported = layer)`), and the board picture's blurred layer is recorded again whenever
the size changes, so on every frame of the growth (0.13.1 stopped it on scroll only). The letter area's 8-13 ms of
input and composing is the list jumping to a letter (a screen of new app cells composed per jump). Neither was changed
blind: the next step is a measurement (the hub's growth with the exported layer and the board picture turned off one at
a time, from the speed check), not a guess.

**Exact next step:** CI's smoke run of 0.15.3 (14h: the board camera opens on the camera-less emulator, says so and
closes); then Med's first series in class: "board camera: ready N ms after opening", "photo N saved … ms after the
shutter", "closed, N photos".

### Addendum: runs 141 and 142

Run 141 (0.15.3, [smoke]) caught a crash in 14h: the board camera died as it opened (`Window.getInsetsController()` in
`onCreate` before the decor view existed: a null pointer inside PhoneWindow on Android 14). On the phone it would have
crashed at the first tap of Board. Fixed by asking for the decor view first (run 142: all three parts pass; 14h
"board camera: opened for "Smoke"", then "The camera could not start (… Device reporting less cameras than
anticipated …)" after CameraX's 6 s of retries on the camera-less emulator, the message shown, Back closes it). The
check now waits for the camera to be gone rather than for the home screen (started from the shell, it lands on 14g's
Settings task).

### Addendum: two skills (Med: "make me a skill with all the liquid design, settings, animations, smoothness and concepts, and one for GitHub saving time")

`.claude/skills/liquid-android/` (SKILL.md + references: design, motion, smoothness, settings, concepts,
project-setup; `assets/kit/`: Setting/SettingsStore/AppSettings, SettingsKit, Glass + LiquidShader, Motion/Presence,
PressFeel, Stagger, DebugLog, Notices, FrameMeter, Changelog, shims and icons, as one `liquid.kit` library module that
compiles on its own against the type-check harness) and `.claude/skills/github-fast-ci/` (SKILL.md + references:
android, other-stacks, releases, triage; assets: Android/Node/Python workflows, release_notes.py, test_failures.py,
settle.sh, smoke-lib.sh, find_text.py, describe_ui.py, ascii_shot.py). Both validated and packaged as `.skill`
files for claude.ai. `tools/typecheck/tc.sh` leaves `.claude/` out. When a session learns something general, add
it to the matching skill reference too.

## Session 2026-11-23 (0.16.0): the wallpaper system, Folders and Island, video wallpapers, the liquid calendar

Med asked for five parts in this order, one release at the end and a pushed checkpoint after each part: (1) fix the
wallpaper library ("tap a wallpaper: its heart disappears; Place, position, Done: the preview disappears and the library
scrolls back to the top") and improve it (Favourites, sort and filter, a full-screen peek on long press, multi-select,
Undo for 10 s after Apply); (2) per-layout wallpapers, recents and collections, look per layout, automatic changes,
a transition when the layout switches, the lock screen; (3) first "the island" (it turned out to mean the Island app),
now Folders and Island: Insular's files through File Shuttle, folders linked per layout, saved copies; (4) live (video)
wallpapers; (5) a liquid calendar out of the clock's date, with the ESSAI 2026-27 dates.

**Step 0, the conflict check (before any code).** Fifteen points went to Med; he took the recommendation for all but
two. "The island" was not part of the launcher: he meant Island (he uses Insular, com.oasisfeng.island.fdroid), so the
island bar, its swipe and long press were dropped, the dock and pill stay as they are, and Part 3 became "Folders and
Island". The settings page is "Folders and Island". A second check (linked folders, "+ Photos", the library against
Insular): "+ Files" next to "+ Photos" (the photo picker cannot see Island); automatic changes keep "saved copies"
apart from the library; folders are linked once and ticked per layout; whether the folder picker shows Insular's tab and
keeps access after a restart is to be seen on his phone; Insular may have no File Shuttle at all, so everything must
also work with ordinary folders (or a folder synced from Insular with Syncthing).

### Part 1: the library (checkpoint)

Why it broke: not a separate destination (Place was already a mode of the same screen and its state already had one
owner). The lists' scroll position lived inside the sheet, which leaves while a picture is placed, so it came back at the
top; the preview vanishing at Done was D158 (0.11.1, "change on Apply"); the heart was drawn white in the corner the
picked picture shrinks away from, on the thick ring. D197 to D201.

| Point | State | What |
|---|---|---|
| Scroll kept across Place | Done | `StudioState.grid(key)`: one `LazyGridState` per list, outside the sheet. |
| Preview stays after Done | Done | `PickerFlow` (core/data, tested): Done keeps the placed picture on screen (finished, blur and dim) until Apply, Cancel or another pick; those fade it back. |
| Heart above the ring | Done | A dark badge inside the picture, drawn last. |
| Favourites tab | Done | It existed; the first row is now Recent, Favourites, All. |
| Sort and filter | Done | Newest, most used (counted from 0.16.0), colour; nine colour families and three shapes (`describeMissing` from thumbnails). |
| Peek on long press | Done | Full screen with the home screen over it; Pick or Back. |
| Multi-select | Done | Select: Favourite, Collection, Recent, Delete (Undo 10 s; asks first when wallpaper notices are off). |
| Collections | Done | One way of grouping (no tags); Favourites counts as one. |
| Undo after Apply | Done | Glass notice with an Undo button, 10 s; the hand-over to MagicOS waits for it. |
| Flow test | Done | `PickerFlowTest` (6) and `WallpaperLibraryTest` (+7); emulator 11b: 16 pictures shared in, Select, peek, pick, Place, Done, Apply, Undo. |
| Share target | Done early | Needed by 11b; Part 3 adds the Insular side. |

### Part 2: per layout (checkpoint)

| Point | State | What |
|---|---|---|
| Own wallpaper per layout (switch, on) | Done | Apply, strip, Shuffle and automatic changes give the current layout its own copy and placement; migration: layouts without one get the wallpaper on screen. |
| Own recents (switch, on), how many | Done | `layoutRecents`, kept to "Recent wallpapers to show" (reused, 4 to 40). |
| Own collection (switch, on) | Done | Made per layout, named after it; the library opens on it when it has pictures; a collection can be given to the layout. |
| Look per layout | Done | Blur and dim (already per layout), colour and tint (new); a picture's own look first; placement per layout. |
| Automatic changes | Done | Per layout; every few minutes and at set times; source "this layout's collection"; only at screen off or while away (D204). |
| Transition on layout switch | Done | Its own setting (default the wallpaper's) and the new Liquid wipe out of the pill. |
| Lock screen | Done | Left alone / same / its own picture (FLAG_LOCK); the old switch read once. |
| Migration | Done | Once, logged as "0.16.0: N layout(s): …". |

Run 144 (checkpoint 1): build, unit tests, the redirect and study parts green; the drawer part caught a crash in 11b.
The test shared media-store pictures with `am start --grant-read-uri-permission`; the launcher got no read permission it
could pass on, and `LiquidEntryActivity` crashed handing them to the home screen (`SecurityException: UID 10192 does not
have permission to content://media/…`), the whole process with it (the redirect too). On the phone any app sharing a
picture without a grant it may pass on would have done the same. Fixed: the entry catches it, logs it and lets the home
screen try without the grant ("Could not read that picture" when it cannot); the test now shares pictures from the
launcher's own file provider.

### Part 3: Folders and Island (checkpoint)

| Point | State | What |
|---|---|---|
| Island's storage through File Shuttle | Done, to test on the phone | "+ Files" and "+ Folder" use Android's picker; its Work tab is the way in when File Shuttle exists. Whether the folder picker shows the tab and keeps the access after a restart is logged ("folder linked: … (work profile, Island; access kept / until the phone restarts)"). |
| Detect Insular, don't assume File Shuttle | Done | By package (Insular first, then Island); the how-to says what to do when the tab never shows (an ordinary folder, or Syncthing). |
| Folders per layout | Done | Hold a folder's chip; none chosen = all; the automatic changes follow. |
| Always copy what is applied | Done | Hand picks join the library (as before); automatic ones are saved copies (D206). |
| Island paused | Done | "Island is paused, showing saved copies"; read again on the profile broadcasts. |
| File Shuttle off and Android refuses | Done | The folder says what happened in plain words; the how-to has the ways in. |
| Share from apps inside Island | Built, to see on the phone | "Set as Liquid wallpaper" is declared for pictures; whether it shows in Insular's apps depends on what Insular lets cross profiles. |
| Strip: folders' newest | Done | From the remembered list, read again in the background. |

Run 145 (checkpoints 1 and 2, with the share fix): all green, the three emulator parts too. 11b passed all eight checks:
16 pictures shared in, Select, the peek, a picked favourite keeps its heart, "after Place and Done the library is where it
was, the picture still picked" (Med's bug), Done keeps the placed picture on the screen, Apply offers Undo, Undo works.

### Part 4: video wallpapers (checkpoint)

| Point | State | What |
|---|---|---|
| In the launcher, the glass bending it | Done; to measure on the phone | ExoPlayer on a TextureView inside what the glass bends (D207). |
| System live wallpaper | Built; to see on the phone | Its own process; Android's screen confirms; the launcher keeps it and counts it as its own. Whether MagicOS's lock screen shows another app's live wallpaper is not known until tried. |
| Trim, loop, once, speed, cross-fade, fill/fit/pan, dim, parallax | Done | Dim, tint, colour and parallax are the wallpaper's own (they apply to the video too). |
| Blur | Done (at re-encode) | Never live: made once in the frames (smoothness rule). |
| Boomerang | Built, measured | Steps back by exact seeks; under 15 frames a second it becomes a loop with a cross-fade and the log says so. Not for GIF and WebP (Android's animated image only plays forward). |
| When it plays; battery rules | Done | `VideoRules`, tested. |
| Frame-rate and resolution caps | Done (at re-encode) | `FrameDropEffect`, `Presentation`. Without a re-encode the video plays as it is. |
| Poster frame | Done | Chosen in the Video tab; the colours and the phone's wallpaper follow it. |
| Re-encode offer on import | Done | Asked by default (H.264, no sound). |
| GIF, animated WebP | Done | Forward only, at their own pace (no speed). |
| Pause off home and with the screen off | Done | Window focus, screen broadcasts. |
| Measure; still frame when too costly | Done; numbers from the phone | D208: "Frames: video wallpaper, the glass …" lines in Med's log. |

Run 147 (checkpoint 4) stopped at compiling: the library thumbnail's description smart-cast another module's property
(`item.video`), which the one-pass local check accepts and Gradle does not. Fixed in run 148 (green), and the local
module lint now flags the pattern (D214). Run 148 changed no `ci/` file, so its emulator parts did not run: the video
check 11c runs with checkpoint 5.

### Part 5: the liquid calendar (checkpoint)

| Point | State | What |
|---|---|---|
| Tap the clock's date | Done | Every design that shows the date (above, below, Minimal, Tall, Stacked's line); a quick tap opens it at once, the long press and the rest of the clock's gestures unchanged (D209). The date is off by default: on in Settings › Home › Clock or a clock's Customise. |
| Grows out of the date, spring, interruptible | Done; to measure on the phone | Shared bounds from the date text; width first, then height; bounce setting; a tap mid-close reopens from where it is. |
| Closes into the date on Back or a pull down, following the finger | Done | Also from the top of a list (nested scrolling), and a tap outside. |
| Other openers later | Ready | `CalendarOpener` (sealed); only `ClockDate` today. |
| Views: month, week strip, agenda, year heatmap | Done | Tabs at the top; month and week swipe sideways; the year's day opens in the month. |
| Liquid blob on the day picked | Done; to measure on the phone | Two springs and a waist, one path, redrawn without recomposing (D211). |
| Events, READ_CALENDAR asked once | Done | Read-only request the first time it opens; "Show my events" asks again; never from the settings preview. |
| Dots or colour bars; calendars and colours | Done | Settings › Calendar › Calendar (Look; More: Calendars and colours). |
| A day's agenda | Done | Holidays, school dates (with their actions), events (open in the calendar app). |
| Quick add | Done | `ACTION_INSERT` on the day picked (next hour today, else 9:00); the calendar stays open across it. |
| ESSAI 2026-27, editable, deletable, indicative | Done | 14 built-in dates in the settings store (D212); in the calendar: pin, a day earlier or later, delete with Undo; in Settings: edit, add, delete, bring back. |
| Countdowns, pinned under the clock | Done | Merged with the exam of the same day; shown even with the study line off; never an exam. |
| Options | Done | Monday first (on, both), week numbers, size (compact, half sheet, full), glass tier, corners, density, today's highlight, dim, bounce. |
| Holidays (Tunisia) | Done | Decree 2021-223 plus the Islamic ones from the Umm al-Qura tables, ≈, movable and hideable, all movable together (D210). |
| Hijri date line | Done | Off by default; follows the shift. |
| One component with the widget; share switch | Done | `MonthDays`; the widget keeps its look and its tap to the calendar app unless it shares (off by default, D211). |
| Calendar settings entry | Done | Eighth entry, three pages, live preview (D213). |
| Tests | Done | `SchoolYearTest` (4), `HolidaysTest` (3), `DayLineTest` (+1), `SettingsMapTest` (+1, eight entries), `CalendarLookTest` (3, home module, CI only); emulator 18: the date opens it on the month with 14b's event in today's agenda, idle frames, the agenda's "DS P2 in …", the year heatmap read from the calendar (its time in the log), the week strip, Back closes it. |

Run 149 (checkpoint 5): build and every unit test green, the redirect part green. The calendar's check 18 passed but
one item: the date opens it on the month, today's agenda shows the test calendar's block, it sits still (0 frames in
3 s), the agenda counts down to the school dates, the week strip shows, Back shrinks it into the date; the year view read
its events ("year 2026: 4 events read in 68 ms") but its legend was below the fold of the half sheet, where the check
looked for it. The legend now sits under the year (better there anyway). The video check 11c ran for the first time:
the shared video was added with its poster and the re-encode question came, but the check's dump was taken while the
question was still closing, so "Apply" was never tapped (the log has no apply at all). It now waits for the button and
prints the screen when it is missing; the wallpaper screen logs what was shared and picked, and an Apply without a new
picture. Same commit: the frame meter's labels for the calendar (D215), the Hijri check, smaller days on the compact
size, a summary for every calendar option.

Run 150: everything green but the video check, and the calendar's check 18 fully green (the Hijri tables are there on
Android: "Hijri today: 14 Jumada al-Akhira 1448"). The video check's new diagnostics showed a real bug: a shared video
was picked ("picked what was shared: video") and Apply then found nothing to apply ("apply: no new picture, only the
looks"). The wallpaper screen cleared the share before picking it; that restarts its effect, and while the re-encode
question showed, the list's scroll waited long enough for the restart to cancel the pick (pictures, without the
question, won the race). It now picks first. Sharing a video would have done nothing on the phone.
