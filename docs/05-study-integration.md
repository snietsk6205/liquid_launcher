# 5. Study companion: tasks and blocks over the calendar

Revised on 2026-11-15 (Fable). The first design (sections 5.1–5.9 of the 2026-11-12 version, kept at the end of this
file) synced two JSON files through a private GitHub repo. The user's assistant proposed the calendar as the bridge
instead. This section compares the two and fixes the format.

## 5.0 Decision: the calendar is the bridge

| | Calendar events (chosen) | `plan.json` / `state.json` on GitHub |
|---|---|---|
| Setup on the phone | Grant calendar access once | Create a private repo and a fine-grained token, paste it in |
| Network code in the launcher | None: Android's own calendar sync carries everything | HTTP client, conditional requests, outbox, retries, token storage |
| Works offline | Yes (the phone's calendar database) | Yes, with a cache and an outbox to build |
| Claude's side | Already writes study blocks to Google Calendar every evening | Needs a session with access to the repo |
| Visible elsewhere | Any calendar app, the watch, the lock screen | Only in the launcher |
| Structure | Semi-structured: a few conventions in title and description | Exact schema, room for statistics |
| Two writers | The same event, but at different times and in different parts (below) | One file per writer, no conflicts by construction |
| Work to build | 1 build | 2–3 builds |

The calendar wins on every point that costs the user time. Its one weakness, two writers on one record, is handled by
dividing the event: **Claude owns the time, the title text and the description's body; the launcher only ever changes
three marks** (the `✓` in front of a title, the `x` inside a task's brackets, and one status line it appends). Claude
writes in the evening, the phone during the day. The GitHub design is not built; it stays documented in case exact
statistics are wanted later.

Trade-off accepted: no per-task ids, priorities or estimates as separate fields. A task is a line of text; what it needs
beyond that goes into the text.

## 5.1 The event format (version 1), for the assistant

Two dedicated Google calendars. The names are settings; the defaults match anything containing these words.

| Calendar | Holds | Event kind |
|---|---|---|
| **Study** | Study blocks | Timed events |
| **Tasks** | Things to do that are not tied to a time | All-day events on the day they are planned for |

### A study block (calendar "Study")

| Field | Content | Example |
|---|---|---|
| Title | `Subject: what` (the part before the first colon is the subject; a title without a colon is all subject) | `Analysis: series convergence` |
| Start / end | The block's time. Not recurring (one event per block) | 14:00 – 15:30 |
| Description | Tasks of the block as checklist lines, then free notes. A line starting with `[ ]` is an open task, `[x]` a finished one. A leading `- ` or `* ` is allowed | see below |
| Colour | Optional. The event's colour (or its calendar's) becomes the block's colour in the launcher | Peacock |
| Location | Optional link or place; a link here (or the first link in the notes) gets an "Open" button | `https://…/sheet4.pdf` |

```
[ ] Exercise sheet 4, ex. 1–5
[ ] Read chapter 3.2
[ ] Flashcards deck 2

Focus on the convergence tests. Sheet: https://example.org/sheet4.pdf
```

### A task (calendar "Tasks")

An all-day event on the day it should be done. Title = the task. Optional notes in the description. A task spanning
several days is an all-day event over those days and shows on each of them until done.

### What the launcher writes back

| Action in the launcher | Change to the event |
|---|---|
| Tick a task inside a block | That line's `[ ]` becomes `[x]` (and back when unticked) |
| Mark a block done | Title gets the prefix `✓ ` |
| Skip a block | Title gets the prefix `✗ ` |
| Tick a task of the Tasks calendar | Title gets the prefix `✓ ` |
| Any of the above, and the focus timer | One status line at the very end of the description, replaced each time: `— launcher: done 15:31 · 2/3 tasks · focus 84 min` |

Nothing else is touched: not the time, not the wording, not the notes. Undoing in the launcher removes the mark again.

### Rules for the evening check-in

1. Read the day's events in both calendars. `✓ ` in front of a title = done, `✗ ` = skipped, neither = not done.
   `[x]` lines = finished tasks. The `— launcher:` line carries when it was finished, how many tasks, and focus minutes.
2. When re-planning an unfinished block, **create a new event** for the new time (or move the old one); do not copy the
   `✓`, the `[x]` marks or the `— launcher:` line into new events.
3. Leave finished events as they are: they are the record.
4. Do not use recurring events for study blocks. (The launcher can still tick one occurrence of a recurring event; it
   then becomes an exception of the series, which is harder to read back.)
5. Plain text descriptions. Descriptions edited in Google Calendar's web page turn into HTML; the launcher reads and
   edits those too, but plain text is safer.
6. Other calendars are never read or changed.

### What it looks like after a day

```
Title:        ✓ Analysis: series convergence
Description:  [x] Exercise sheet 4, ex. 1–5
              [x] Read chapter 3.2
              [ ] Flashcards deck 2

              Focus on the convergence tests. Sheet: https://example.org/sheet4.pdf
              — launcher: done 15:31 · 2/3 tasks · focus 84 min
```

## 5.2 On the phone

- Reading: `CalendarContract.Instances` for today (and the next days for the agenda), limited to the chosen calendars.
  No account, no network. A content observer refreshes the launcher when the calendar changes; "which block is now" is
  worked out when the launcher appears and once a minute while it is on screen. No alarms, no background work.
- Writing: an update of the event's title or description through the calendar provider; Android's sync sends it to
  Google. For one occurrence of a recurring event, an exception is written.
- Permissions: read and write calendar, asked the first time the study companion is switched on. Without write
  permission the launcher still shows everything and keeps ticks to itself (they are then not visible to Claude).

## 5.3 What you see

- **Now card** (widget): the block running now (or the next one with a countdown), time left as a bar, its tasks with
  tap-to-tick, "Done" and "Skip", and three quick actions: focus timer, note to Claude, study plan.
- **Today** (panel, and a widget): today's blocks in time order with their tasks, the Tasks calendar's tasks for today,
  overdue tasks of the last two weeks on top, tomorrow's first block at the bottom.
- **Glass notices**: a block is starting, a block ends in five minutes, tasks due today (once, the first time the
  launcher shows that day).
- **Quick actions**: "Tell Claude" opens the Claude app's share sheet with a ready text (what was planned, what was
  done, focus minutes, space for a note); "Study plan" opens a link or app of your choice; "Focus" runs a timer to the
  end of the block and writes the focused minutes into the status line.

## 5.4 Later (Phase 6)

Study mode (a layout switch during blocks), friction for distracting apps, profiles. They read the same blocks.

---

# Appendix: the first design (GitHub files), not built

## 5.1 Evaluation of the proposed design

Your proposal: one `tasks.json` in a private GitHub repo, written by Claude, read and written back by the launcher with a fine-grained token.

It is the right approach. GitHub gives free hosting, history, offline-friendly conditional requests, and Claude can already write to a repo. One change makes it robust:

**Problem with a single file:** two writers on one file. If Claude regenerates the plan while the phone has unsynced completions, one side must merge or overwrite, and the GitHub API rejects the stale write.

**Fix: two files, one writer each.**

| File | Writer | Content |
|---|---|---|
| `plan.json` | Claude only | What should happen: subjects, blocks, tasks, priorities |
| `state.json` | Launcher only | What did happen: completions, block results, quick-added tasks, statistics |

Neither side ever needs to merge. Claude reads `state.json` before writing a new plan. The launcher shows `plan.json` overlaid with `state.json`.

Alternatives considered and rejected: Google Tasks (OAuth project on the device, no Claude connector), tasks inside calendar event descriptions (fragile to edit), a GitHub Gist (cannot be covered by a fine-grained token).

## 5.2 Repository

A separate private repo, for example `study-sync`, so the phone's token cannot touch the launcher's source code.

```
study-sync/
├── plan.json            # Claude writes
├── state.json           # launcher writes
└── archive/2026-11.json # Claude writes: finished tasks moved out of plan.json (optional)
```

**Token**: fine-grained personal access token, repository access = only `study-sync`, permission = Contents: read and write. Entered once in Settings → Study → Sync, stored encrypted with a key in the Android Keystore, never written to backups or logs.

## 5.3 `plan.json` (schema version 1)

```json
{
  "schema": 1,
  "generated_at": "2026-11-12T06:30:00+01:00",
  "timezone": "Europe/Paris",
  "plan": { "id": "p2", "title": "P2 Daily Plan" },

  "subjects": [
    { "id": "math", "name": "Mathematics", "color": "#6C8EBF", "icon": "function" }
  ],

  "blocks": [
    {
      "id": "b-20261112-1",
      "title": "Analysis: series",
      "subject": "math",
      "kind": "study",
      "start": "2026-11-12T14:00:00+01:00",
      "end": "2026-11-12T15:30:00+01:00",
      "calendar": { "event_id": "abc123def456" },
      "task_ids": ["t-20261112-01", "t-20261112-02"]
    }
  ],

  "tasks": [
    {
      "id": "t-20261112-01",
      "title": "Exercise sheet 4, ex. 1–5",
      "notes": "Focus on convergence tests",
      "subject": "math",
      "date": "2026-11-12",
      "due": "2026-11-13T23:59:00+01:00",
      "priority": 1,
      "estimate_min": 45,
      "block_id": "b-20261112-1",
      "order": 10,
      "tags": ["exam"],
      "links": [{ "label": "Sheet 4", "url": "https://example.org/sheet4.pdf" }],
      "subtasks": [{ "id": "s1", "title": "Ex. 1–3" }, { "id": "s2", "title": "Ex. 4–5" }],
      "status": "open",
      "status_at": "2026-11-12T06:30:00+01:00"
    }
  ],

  "inbox_ack": ["q-1794491400"]
}
```

Field rules:

| Field | Rule |
|---|---|
| `schema` | Integer. The launcher refuses a higher major version and says so; unknown fields are ignored, so Claude can add fields freely. |
| `id` (block, task, subtask) | Stable forever. Rescheduling keeps the id. Never reused. |
| `blocks[].kind` | `study`, `review`, `break`, `exam`, `other`. |
| `blocks[].calendar.event_id` | Google Calendar event id, used to match the calendar entry (see 5.6). Optional. |
| `tasks[].date` | The day the task is planned for. `due` is the real deadline and optional. |
| `tasks[].priority` | 1 (highest) to 4. |
| `tasks[].block_id` | Optional. Tasks without a block appear under "Anytime today". |
| `tasks[].order` | Sort key inside a block or day. |
| `tasks[].status` | `open`, `done`, `dropped`. Claude sets `done` when you tell it in chat. |
| `status_at` | Time of the last status change. Used by the merge rule. |
| `inbox_ack` | Ids of quick-added tasks Claude has taken over; the launcher then removes them from its inbox. |

Scope of the file: undone tasks from the past, today, and the coming 14 days. Older finished items move to `archive/`.

## 5.4 `state.json` (schema version 1)

```json
{
  "schema": 1,
  "device": "honor-90-lite",
  "updated_at": "2026-11-12T15:32:10+01:00",

  "tasks": {
    "t-20261112-01": {
      "status": "done",
      "at": "2026-11-12T14:41:10+01:00",
      "subtasks": { "s1": { "done": true, "at": "2026-11-12T14:20:00+01:00" } }
    }
  },

  "blocks": {
    "b-20261112-1": {
      "status": "done",
      "at": "2026-11-12T15:31:00+01:00",
      "focus_min": 84,
      "friction_shown": 2,
      "friction_passed": 1
    }
  },

  "inbox": [
    { "id": "q-1794491400", "title": "Ask about ex. 3", "created_at": "2026-11-12T14:50:00+01:00" }
  ]
}
```

- `blocks[].status`: `done`, `partial`, `skipped`.
- `inbox`: tasks you add from the launcher (`+ text` in search or the Quick add widget). Claude turns them into real tasks and lists their ids in `inbox_ack`.
- The launcher prunes entries whose ids are no longer in `plan.json` and are older than 7 days.

## 5.5 Merge rule and sync

**Effective status of a task** = whichever is newer: `plan.tasks[i].status_at` or `state.tasks[id].at`. So a completion on the phone wins over an older plan, and Claude re-opening a task later wins over an older completion.

**On the phone:**

1. The local database is the source of truth for the UI. Tapping a checkbox writes locally at once, with haptic and a 5-second undo.
2. Changes go into an outbox. A debounced job (10 s) uploads `state.json` when there is network.
3. Download: `GET /repos/{owner}/{repo}/contents/plan.json` with `If-None-Match`. An unchanged file answers `304` and costs no rate limit. Runs when the launcher becomes visible (at most every 5 min), every 30 min in the background via WorkManager with a network constraint, and on demand.
4. Upload: `PUT …/contents/state.json` with the last known `sha`. The launcher is the only writer, so conflicts should not occur; on `409` it refetches the sha and retries once.
5. Offline: everything works from the cache; a small cloud icon with a count shows pending changes. `401` shows "token expired" in the Study panel.

**On Claude's side** (each planning run): read `state.json` → fold completions and inbox into the plan → write `plan.json` (and events to Google Calendar). This can run in any Claude session that has access to `study-sync`, or as a scheduled task each morning.

## 5.6 Calendar

- Read through `CalendarContract.Instances` with the calendar permission. No Google sign-in in the launcher; the phone's own calendar sync does the transport.
- **Which events are study blocks**: by default every event in the calendars you select (a dedicated "Study" calendar is the cleanest). Alternatives: title contains a text, or the description contains a marker line such as `p2:b-20261112-1`.
- **Matching an event to a plan block**, first hit wins: marker in the description → Google event id equals `calendar.event_id` → same day, overlapping time and similar title.
- **The calendar decides the time.** If you drag a block to another hour in Google Calendar, the launcher follows the calendar; `plan.json` supplies the tasks and metadata. Calendar events with no plan block still show as blocks without tasks.
- Live updates through a content observer while the launcher is visible. No alarms: "which block is current" is computed when the launcher appears and once a minute while it is on screen.
- **Marking a block done**: stored in `state.json`. Optionally also written to the calendar (prefix "✓ " on the title), off by default because it needs write permission.

## 5.7 What you see

**Now card** (widget and top of the Study panel)

```
╭───────────────────────────────╮
│  ◔ 52 min left   Analysis     │   progress ring, subject color
│  14:00 – 15:30                │
│  ☐ Exercise sheet 4, ex. 1–5  │   tap = done (haptic, undo)
│  ☑ Read chapter 3.2           │
│  [ Block done ]   Next: 16:00 │
╰───────────────────────────────╯
```

Between blocks it shows the next block with a countdown and any "Anytime today" tasks. With nothing planned it collapses to one line.

**Today list**: blocks in time order, tasks nested, overdue tasks on top, done items struck through or hidden.

**Week view**: seven columns, blocks as colored bars filled by completion, tap a day to open its list.

**Study panel**: Now card, Today list, Week strip, sync status. Opened by swipe (default: the page left of home) or action.

All of these work offline and can also be placed as widgets on any page.

## 5.8 Study mode, friction, profiles

- **Study mode** = the Study profile being active. During a calendar study block (with configurable lead-in and lead-out) the launcher switches to the Study layout: study apps in front, folders and apps tagged as distracting hidden from home, drawer and suggestions (still reachable through search if you choose). A chip on the home screen shows the mode and lets you override it for a chosen time.
- **Mindful friction**: see doc 2, section 2.7. Passing through is logged per block, which gives Claude a real signal for planning.
- **Profiles** generalise this: Study / Free / Night, each with layout, wallpaper dim, theme and hidden tags, switched by gesture, time or calendar.

Refinement of your ideas: study mode and profiles are one mechanism, not two features. Friction stays separate because it can also apply outside study blocks.

## 5.9 Privacy

The launcher talks to exactly one host for this feature (`api.github.com`) and only to your repo. Calendar data never leaves the phone. Statistics upload can be turned off; then `state.json` carries completions only.
