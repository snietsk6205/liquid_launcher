# Triage: from a red run to the cause in a few minutes

Contents: 1 Get the facts · 2 Read the logs fast · 3 Flaky or real · 4 Fixing a flaky test properly · 5 Slow runs ·
6 Reporting

## 1. Get the facts

With the GitHub MCP tools (Claude Code cloud sessions):
- Runs: `actions_list` → `list_workflow_runs` (filter `branch`, `perPage: 1-3`): status, conclusion, run number,
  head commit.
- Jobs: `actions_list` → `list_workflow_jobs` (run id): each job's and step's conclusion and timing.
- Logs: `actions_get` → `get_workflow_run_logs_url` (run id), then download the zip:

```bash
D=$SCRATCH/run141; mkdir -p "$D" && cd "$D"
curl -sS -L -o logs.zip "<logs_url>" && unzip -o -q logs.zip -d x
for f in x/*/"<step file name>.txt"; do echo "== $f"; grep -E "FAIL:|SMOKE RESULT|gave up\)" "$f" | cut -c30-250; done
```

  `get_job_logs` with `failed_only: true` and a short `tail_lines` also works for a quick look, but the zip keeps
  every job and step as separate files and can be grepped freely (the 28-character timestamp prefix is cut with
  `cut -c30-`).

With the `gh` CLI (a local machine): `gh run list -b <branch> -L 3`, `gh run view <id> --log-failed`,
`gh run download <id>` for artifacts (screenshots, app logs, logcat).

Waiting for a run: start a background timer (e.g. `sleep 300` in the background) and check when it fires; do not
poll in a tight loop. Build + unit tests take ~3-5 min, emulator parts ~5-6 min more.

## 2. Read the logs fast

| Look for | Where |
|---|---|
| `FAIL:` lines and `SMOKE RESULT` | the test step's file |
| `FATAL EXCEPTION`, `Caused by:`, `at your.package` | the crash check / logcat in the same file or artifact |
| `e: file:///…` / `error:` | the Gradle step (compile errors) |
| `FAILED …Test.method` | "Summarise failed unit tests" |
| `SLOW WAITS`, `TIME PER SECTION` | end of the test step: where the minutes went |
| the app's own log lines around the failing check's time | the "App log" section: what the app decided |

Read the app's log at the failing moment before guessing: the cause is usually one line ("released at 0.40, 710
px/s" = the swipe was too short to count; "the system took the keyboard back while the launcher moved it" = the
test's Back landed mid-animation).

## 3. Flaky or real

- Compare with the last green run of the same check: same inputs, what differs in the app's log?
- Real regression: the failing behaviour follows from a change in this push (read the diff for that path).
- Flaky: the check depends on timing the emulator does not guarantee (gesture speed, an animation still running,
  a background service reconnecting). It may pass on rerun, but rerunning without understanding wastes 10 minutes
  and hides real bugs.
- A flaky failure can still reveal a real bug: in Liquid Launcher a Back pressed during the keyboard's rise was
  undone by the app (the keyboard came back); the test only exposed it because its timing varied. Fix the app first
  if the user could hit the same path.

## 4. Fixing a flaky test properly

- Make the test wait for the condition (or for the app's log line) instead of a fixed time.
- Make input robust (slower swipes; a second attempt when the app's log says the first did not register).
- Make the app log what it decided, so the test can tell (e.g. ": opens" / ": closes" after each release).
- Never delete, skip or loosen the check to get green; never rerun a failing release hoping it passes.

## 5. Slow runs

- `TIME PER SECTION` and `SLOW WAITS` show where the minutes went; the job/step timings in `list_workflow_jobs`
  show cache restores and boots.
- A cache that never hits: its key changes every run, or the branch cannot write it (`cache-read-only`).
- An emulator that takes minutes to be usable: snapshot saved before the phone was calm; re-save with `settle.sh`.
- Runner variance is real (2-3x): compare the CPU line before blaming a change.

## 6. Reporting

Tell the user in plain words: which run, green or which check failed, the cause (one sentence, with the number from
the log), what was changed, and whether anything was published (with the link and the build number).
