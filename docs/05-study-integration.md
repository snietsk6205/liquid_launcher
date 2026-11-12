# 5. Tasks and calendar integration

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
