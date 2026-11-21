# Liquid Launcher: my feedback on 0.11.2 (brief for the next session)

Continue Liquid Launcher (repo `snietsk6205/liquid_launcher`). Start from `main`: it has everything up to 0.11.2
(PR #1 merged as `8a9f1ac`). Work on the branch this session gives you. 0.11.2 (build 105) is released and on my
HONOR 90 Lite (MagicOS). This session is my feedback after using it, with my answers to the last session's questions.

## Attached (if I attach them again; the key numbers are copied below)
1. A screenshot of the search page with the flashcard of the day: its formulas show as plain text (`f_n`, `∫ f_n dμ`).
2. My debug log of 2026-11-21, 08:06–12:49.

## Read before coding (max 15 minutes)
- `docs/FABLE_LOG.md`, last section ("0.11.2, Med's answers on 0.11.1"): what exists, the CI changes and their
  timings, known limits, questions.
- `DECISIONS.md` D162–D167, and `CHANGELOG.md` 0.11.2.
- `docs/05-study-integration.md`: the calendar contract and the `launcher-data` JSON (the flashcard is there).
- `docs/reference/wallpaper-stoped.wmtheme.json`: my WM Keyboard theme file (its format, for item 5).
- `tools/typecheck/README.md`: run `fetch.sh` once, then `tc.sh` and `ut.sh` after every change.

## How we work (unchanged)
- No local Android build: the proxy blocks dl.google.com. Every compile check is a CI run. The in-app updater reads
  GitHub's "latest release".
- No approvals: I am not watching. When unsure, take the most sensible choice, write it under "Questions for Med"
  in `docs/FABLE_LOG.md`, and continue.
- Releases: at most 2. A safety release after items 1–3 (0.11.3) and a final release at the end (0.12.0), published
  only when the head commit message contains `[release]`; never write that marker in any other commit message, not
  even in its body: the workflow reads the whole message. Each release has its CHANGELOG.md entry.
- CI: every push builds and runs the unit tests; the emulator test runs for `[release]`, for a message with
  `[smoke]`, for manual runs and when `feature/redirect/**`, `ci/**` or `.github/workflows/**` changed. So put
  `[smoke]` in the message when a change to the home screen or search needs the emulator. Run tools/typecheck
  (`tc.sh`, `ut.sh`) before every push. `ci/smoke.sh` ends with each section's time and its slow waits.
- No more CI tuning. One rule stays: if a run's system log shows "ANR in" (not responding) again, drop the
  emulator snapshot cache (boot cold each run, keep the system image cache, then `ci/settle.sh`, then the test).
- Logs and artifacts: `get_job_logs` with a short tail, or download the job's `logs_url` and the run's artifacts
  (`smoke-output`: `launcher.log`, `logcat.txt`, screenshots) with curl.
- Never leave the branch broken. Work in the order below; unfinished items stay on the branch for the next session.
- Quality bar: no placeholder settings, no fake effects, nothing that does nothing; every visual option gets a live
  preview. If Android, MagicOS or the keyboard makes something impossible, log why and build the best real
  alternative.
- Every new option or feature goes to its right place: the right entry › page › tab in `SettingsMap` (with search
  keywords), and on the home screen where I would look for it.
- Calendar contract: do not break it (the `✓ `/`✗ ` titles, `[x]` lines, `— launcher: …` lines after `P2-BLOCK`,
  `Note: …` events, the `launcher-data` event are read by my evening check-in); extend it only in ways old data
  still reads. No network calls to Claude. No data loss: migrate, never reset my settings, layouts, tags, themes or
  flashcard progress. Keep everything smooth on a mid-range phone and measure with the frame meter.
- Context: keep reads short (grep and line ranges, not whole files). If you get close to the limit, stop at a clean
  point, push, and write the exact next step at the end of this file.

## My answers to the last questions (2026-11-21)
- Search: "Keyboard slides with the pill" (the launcher drives the keyboard's slide).
- Keyboard: all four: search-friendly typing, ask for it during the swipe, a matching keyboard theme, a Liquid tool
  in the keyboard.
- Flashcards: "A small deck with repeats".
- Math: "Claude writes LaTeX, rendered".

## What to do, in this order

### 1. The clock shows the wrong time for some seconds after I unlock (bug)
Cause found by the last session: every home clock and timer uses `rememberMinuteTick()`
(`feature/home/.../Clock.kt`), a coroutine `delay` to the next minute. That delay counts uptime, which stops in deep
sleep, so after the screen was off the old minute stays until the rest of the delay has run. Users: `Clocks.kt`,
`PillStudy.kt`, `Widgets.kt`, `SearchStudy.kt`, `CalendarWidget.kt`, `HomeGrid.kt`. Fix: one shared time source,
read from the wall clock and refreshed on screen on / unlock, on resume, on `ACTION_TIME_TICK`, `TIME_SET` and
`TIMEZONE_CHANGED`, with the minute delay kept only as a backup. A unit test for the timing; log a line when the
shown minute was behind at a refresh (how far).

### 2. Search: the keyboard slides with the pill, and the pill's size
- From my log: the keyboard opened at 933 px every time and the field waited at 933 px, but the keyboard started 616,
  227, 518, 201 and 724 ms after search opened, and 3 of 6 times it had to be asked again ("no keyboard yet, asking
  again"). So the pill sits above an empty gap until the keyboard arrives.
- Build what I chose: the launcher drives the keyboard's slide (`WindowInsetsControllerCompat
  .controlWindowInsetsAnimation` for the IME), in step with the search transition, and asks for the keyboard as
  soon as the swipe towards search starts. If the keyboard or MagicOS refuses or cancels the control, fall back to
  "the pill rides on the keyboard" (never above it, `max(nav, ime)`). Log each opening: which path, when the
  keyboard started, where the pill and the keyboard were. Keep `KeyboardLine` (`core/common`) for the height.
  Today's code: `SearchOverlay.kt` (`SearchKeyboard`, `KeyboardWatch`), `HomeController.searchFieldLine`.
- New settings, with a live preview: the search pill's height, its width, and the gap above the keyboard.

### 3. Recents: my launcher and HONOR's fight for a moment
From my log: leaving recents, HONOR's home shows, then "redirect in 80 ms", and my launcher is drawn 200–250 ms
after the trigger; once "system launcher appeared, home screen visible, redirect in 260 ms" and drawn 401 ms after.
That swap is what I see. Make it shorter and hide it: check whether the 80 and 260 ms waits can be shorter without
breaking the recents checks (CI checks 4, 4b, 5, 5b, 5c run on every `feature/redirect` change); make my launcher's
arrival one smooth transition over HONOR's home instead of a cut; keep "the launcher's last picture" fresh after the
drawer, search or keyboard closes, so it can be used. Measure with the "launcher drawn … after trigger" lines.

Then release 0.11.3 (CI green, `[release]` only on that commit).

### 4. Pending from 0.11.2
- "Still glass in motion" waits for my Speed check log (Settings → Diagnostics → Speed check). If I attach it,
  follow the last FABLE_LOG entry's "Exact next step"; if not, leave it.
- I still owe the test photo (Settings → Study companion → More) and a search log; ask again under "Questions for
  Med" if they are not attached.

### 5. My keyboard and the launcher (WM Keyboard, https://github.com/wasi-master/wmkeyboard)
It is written in Kotlin and Compose. It has a toolbar of tools you drag onto it, clipboard history, a theme editor
with Material You and theme files that export, automation intents (Tasker can switch its layouts, modes and
languages) and a plugin API (Lua plugins, addon repositories). Read its docs for each point before building it.
- Search-friendly typing: search asks for no autocorrection on app names and a Search/Go key. If its automation
  intents allow it, a setting that switches it to a layout made for search while search is open and back after.
- A matching keyboard theme: export a `.wmtheme.json` (format: `docs/reference/wallpaper-stoped.wmtheme.json`) in
  the launcher's current colours and glass, to save or share, with a preview. Check that the keyboard can import a
  theme file; if it cannot, log it and do not ship a button that does nothing.
- A Liquid tool in the keyboard: an entry point in the launcher that opens search with text, or asks Claude with it
  (an exported intent, and "Liquid" in the text selection menu through `ACTION_PROCESS_TEXT`); and, if its plugin
  API allows opening another app, a plugin in `integrations/wmkeyboard/` with install steps for me.

### 6. Flashcards: a small deck with repeats
- Today: Claude writes one card a day into the `launcher-data` event (`"flashcard": {"module", "q", "a"}`); Wrong
  adds a calendar note "flashcard wrong <module>: <question>" for my evening check-in; Right only hides it for the
  day (`StudyCalendar.answerCard`, `SearchStudy.FlashcardCard`).
- Build: `launcher-data` may hold `"flashcards": [{"id", "module", "q", "a"}, …]`; the single `"flashcard"` still
  reads as a one-card deck. Cards are kept in a local deck (new ones added, never lost when a new event comes).
  Repeats: wrong comes back tomorrow; right waits longer each time (1, 3, 7, 14, 30 days). Search shows the first
  card due and how many more are due, and "All done for today" at the end. Every wrong answer still writes its
  `Note:` for the check-in. Migrate today's "answered" state.
- Update `docs/05-study-integration.md`, and write me the exact text to paste into my study plan doc and my
  assistant's instructions, so Claude writes `flashcards` with LaTeX (item 7).

### 7. Math on cards: Claude writes LaTeX, the launcher draws it
- Claude writes `$…$` inline and `$$…$$` on its own line, in questions and answers. The launcher draws them offline,
  with no web page: real subscripts and superscripts, fractions, roots, integrals, sums and limits with their
  bounds, Greek letters, arrows, ∞, ℝ (`\mathbb`), `\text`. Text without `$` shows as today.
- Choose between a maintained Android LaTeX library (check its licence, its size in the APK, Compose use) and a
  small renderer of that subset in Compose; pick by how my measure theory cards look. Unit tests for the parsing,
  and one emulator check with a formula card (`[smoke]`).

Then release 0.12.0 at the end.

## End of the session
The last push includes the final `[release]` with its CHANGELOG entry and a FABLE_LOG entry: the version released,
each item above done / partly done / not started, known bugs, "Questions for Med", and the exact next step. Then
open a PR from your branch into `main` titled "Merge 0.12.x into main" and tell me to merge it with "Create a
merge commit" (a squash or rebase merge would publish again). Don't merge it yourself.
