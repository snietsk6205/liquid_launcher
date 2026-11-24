# Concepts: diagnostics, feedback loop, documents, working with Med

Contents: 1 The debug log · 2 Diagnostics report · 3 Notices · 4 What's new and the updater · 5 Time and background
work · 6 The documents · 7 Working with Med · 8 A session, start to end

## 1. The debug log

Code: `assets/kit/.../diagnostics/DebugLog.kt`. A ring buffer (2000 entries) free of Android classes, so plain JVM
unit tests can log; the app installs a sink that mirrors entries to logcat. Levels by verbosity: `ERROR`, `EVENT`,
`DECISION`, `TREE` (very detailed, off by default). The settings app shows it, filters it and exports it as text.

**Write log lines as sentences a person can read**, with the numbers that explain what happened:

```
10:45:10.949 E Home: search: the keyboard rises with the pill: ready 463 ms after asking (search at 1.00, the field at 951 px; it goes to 933 px)
11:00:06.650 E Study: board photo: saved in Pictures/ESSAI/Stat Des cours (4967 KB; the camera said done)
12:03:44.229 E Home: search: released at 0.67, -10596 px/s: closes
```

- One line per decision the app takes on its own (which path, why, how long), never one per frame.
- Include the cause when something did not happen ("no keyboard came", "the camera said cancelled").
- Name things the way the UI names them, so the user can read his own log.
- These lines are how most bugs got found: the user sends the log, the numbers show what the eye could not
  (a keyboard 1188 px tall for 25 ms, a pill at 1.15 of its travel, a release at 0.40 of the way).

## 2. Diagnostics report

A Diagnostics screen in System builds one report the user can share as text: sections marked `[OK]`, `[WARN]`,
`[FAIL]`, `[INFO]` (device, display and refresh rates, app version and build, permissions, services, what each
special API returned, a glass benchmark), then the debug log. Each check runs in a try/catch that turns an exception
into a `[FAIL]` line with the exception's name, so the report itself never crashes. A "speed check" plays the main
animations with each costly effect turned off in turn and adds one frame line per step (`smoothness.md` §2).

## 3. Notices

Code: `assets/kit/.../diagnostics/Notices.kt`. Any part of the app (data layer, a service, another activity) posts a
`Notice(kind, text, detail, action)` to a queue in the lowest module; the main screen shows them one at a time as a
glass label. Each kind has its own switch and how long it may wait for the app to come to the front (seconds for a
layout switch, hours for an update). Use them instead of toasts for things that happened while the user was away
("Board photos: Stat Des cours · 3 photos in Pictures/ESSAI/Stat Des cours").

## 4. What's new and the updater

- **CHANGELOG.md is the single source**: CI copies it into the app's assets (`app/src/main/assets/CHANGELOG.md`),
  the app shows the new version's section once after an update ("What's new" card, "Got it") and on its Updates
  screen; CI publishes the same section as the release notes. Parser: `assets/kit/.../diagnostics/Changelog.kt`.
- **In-app updater** (no store): on request, or once a day when Settings opens if the user opted in, ask GitHub for
  the latest release, compare the build number in the APK's file name (`App-<version>-build<N>.apk`) with the
  app's `versionCode` (= the CI run number), download into the cache, hand it to the system installer through a
  FileProvider (`REQUEST_INSTALL_PACKAGES`, "install unknown apps" asked once). Builds are signed with the same key,
  so every update installs over the previous one and keeps everything (D19, D27, D71).

## 5. Time and background work

- **One time source** (`WallClock`): every clock and timer reads it; it is refreshed from the wall clock on the
  system's minute tick, time/zone changes, screen on, unlock, and when the app starts or resumes. A coroutine
  `delay` to the next minute counts uptime, which stops in deep sleep: after the screen was off, clocks showed the
  old minute for seconds (D168).
- **Nothing in the background unless asked**: read data when the app comes to the front and when the data changes
  while visible (content observers); timers are two timestamps, not a running service; jobs (daily backup) use
  JobScheduler (D88, D142).
- A notification listener can snooze notifications during focus and give them back after (never dismiss them).

## 6. The documents

| File | What | Format |
|---|---|---|
| `DECISIONS.md` | every choice made without asking | `\| D<n> \| date \| decision \| why \| cost to reverse \|`, newest at the bottom; "(open, Q<n>)" when a question in the questions doc still decides it; "Revises D<n>" when it replaces one |
| `CHANGELOG.md` | what changed, for the user | `## <version> — <date>`, then `### Added`, `### Changed`, `### Removed`, `### Fixed`; items in plain words saying where things are (Settings › Entry › Page › Tab); a version built but not released alone gets `*Not published on its own: …*` under its heading |
| session log (`docs/FABLE_LOG.md`) | one entry per session or feedback round | what was asked (quoted), what the user's log showed (numbers), a table `\| # \| Point \| State \| What \|` (Done / Done; on the phone, to see / Partly / Not started / Looked into, not changed), known limits, **Questions for Med**, **Exact next step** |
| `docs/07-questions.md` | open questions | table `\| # \| Question \| Default if no answer \|`: work continues with the default |
| `docs/NEXT_SESSION_PROMPT.md` | brief for the next session | what to read first (15 min max), how we work, what to do in order, the end-of-session checklist |
| design docs `docs/01-…07-` | feasibility, screens, settings tree, roadmap with "what to test" per phase | Markdown, readable on the phone in GitHub |

Comments in the code carry the version and decision that explain them (`// 0.15.2 (D194): …`), and the reason, with
the measurement when there was one.

## 7. Working with Med

- He uses the app daily on his phone and sends: what he wants (often several things in one message, in quick
  English), screenshots, his settings export, the diagnostics report and the debug log. Read the log: the numbers
  usually show the cause.
- **Ask before big work**, a few questions at a time (two to four), each with the options and a **recommended one
  first**, phrased so the answer changes what you build. He answers quickly; when he says "remove it if you want",
  remove it; when he says none of the ideas are good, drop them.
- **Tell him where settings moved** and why, in the final message.
- **Releases only when he says so** (or approves when asked). Never push a release marker on your own.
- Report outcomes plainly: what changed for him, what he should try on the phone, what was not done and why
  (e.g. "looked into, not changed: it needs a measurement on your phone"). Link the release and give the build number.
- Language: plain sentences, no jargon (he is a student; the app's own UI text follows the same rule).

## 8. A session, start to end

1. Read the last session log entry and its "Exact next step", the relevant decisions, the changelog head.
2. Read the user's log and attachments; list his points.
3. Ask the questions that change the design (with defaults); work on the rest meanwhile when possible.
4. Build in small verified steps: local type check and unit tests before every push; push with `[smoke]` when the
   emulator should check it; read the CI result; fix the test's flakiness properly when it is the test.
5. Update CHANGELOG, DECISIONS, the session log; bump the version.
6. When he approves, release (`[release]` on that commit only), wait for the publish job, then report with the link.
