---
name: github-fast-ci
description: How to make GitHub Actions fast, cheap and readable, and the push-CI-release loop quick, for any of Med's repos (Android/Gradle first, also Node, Python, web, Docker) - learned on Liquid Launcher, where a push went from ~16 minutes to ~3 and a release from ~18 to ~10. Covers checking locally before pushing, commit-message markers ([release], [smoke], [skip ci]), concurrency (and why never to push while a release run is going), caches that work on every branch, Android emulator snapshots and parallel test parts, waiting for conditions instead of sleeping, releases built from CHANGELOG.md with versionCode = run number, flaky-test handling, and reading a failed run in seconds (download the log zip and grep). Use this skill whenever creating or editing a workflow, setting up releases, when a CI run is slow, failing or flaky, when watching a run after a push, or when Med mentions GitHub, Actions, CI, cache, build time, "save time", releases or a new project like skwd wall, even if he does not name the skill.
---

# GitHub fast CI

Liquid Launcher's CI, tuned over 140+ runs: a push builds and unit-tests in 2.5-5 minutes; the Android emulator
test runs as three parallel parts of about 4-6 minutes only when asked or when risky paths changed; a release takes
about 10 minutes end to end and publishes only when every check passed. Before: about 16 minutes per push (two
thirds of it the emulator test: image download, cold boot, about 250 s of fixed sleeps).

| Need | Read |
|---|---|
| Android: Gradle cache, emulator snapshot, parallel parts, the test script | `references/android.md` |
| Node, Python, static sites, Docker, Flutter | `references/other-stacks.md` |
| Versions, tags, release notes from CHANGELOG, one asset per release | `references/releases.md` |
| A run failed or is slow: get the logs, find the cause, flaky or real | `references/triage.md` |
| Ready files | `assets/` (workflows, `release_notes.py`, `test_failures.py`, `settle.sh`, `smoke-lib.sh`, UI helpers) |

## The rules (and why)

1. **The fastest run is the one you do not need.** Check locally before every push (type check, unit tests,
   `bash -n` on scripts, `python3 ci/release_notes.py <version>` before a release). Batch related changes into one
   push. A CI round trip is 3-11 minutes; a local check is seconds.
2. **Cheap checks on every push, expensive ones on demand.** Build + unit tests always. Emulator / end-to-end /
   deploy only for: a `[release]` commit, a message containing `[smoke]`, a manual run, or a push that changed the
   paths where they matter (computed with `git diff --name-only before..after`). New or rewritten branches run
   everything (the diff is unknown).
3. **Cancel what a newer push supersedes**: `concurrency: { group: build-${{ github.ref }}, cancel-in-progress: true }`.
   Consequence: **never push to a branch while its release run is running** - the push cancels the release. Commit
   locally, wait for the run to finish, then push.
4. **Markers are read from the whole commit message, body included.** Put `[release]` only in the release commit;
   never quote a marker in any other commit message (a commit describing the CI rules once published a release by
   accident). GitHub's own `[skip ci]` / `[ci skip]` / `[no ci]` / `[skip actions]` skip the run entirely (use for
   docs-only pushes when nothing must be checked).
5. **Cache what is slow, on every branch.** `gradle/actions/setup-gradle` saves its cache only on the default branch
   unless `cache-read-only: false`; feature branches (`claude/**`) then start cold every time. Same idea for npm/pip
   caches keyed on the lockfile.
6. **Snapshot slow environments, wait for conditions.** Cache the emulator's system image and a booted, settled AVD
   snapshot; load it read-only (`-no-snapshot-save`). In test scripts, never `sleep N` for something to happen:
   poll for it with a timeout, and report every wait that took 5 s or more.
7. **Split long suites into parallel matrix parts** (`fail-fast: false`) along the checks' real dependencies; three
   runners in parallel cost the same minutes but return results 3x sooner.
8. **One source for releases**: the version in one file, the notes in `CHANGELOG.md` (checked before building),
   `versionCode`/build number = `github.run_number`, tag `v<version>`, one asset per release (a newer build of the
   same version replaces it). The app bundles the same changelog for its "What's new".
9. **Make failures cheap to read.** A step that prints failed unit tests briefly (`test_failures.py`, `if:
   failure()`); test artifacts uploaded with `if: always()`; scripts that end with a summary (result, failed checks,
   time per section, slow waits) and print screens as ASCII into the log.
10. **A flaky test gets fixed, not skipped.** Read the logs, find why it flaked (e.g. an injected swipe arrived too
    short to count), make the test robust to exactly that (swipe again when the app's own log says it did not
    register), keep the check. Never disable or loosen a test to get green.

## After a push (with Claude Code)

1. Find the run: GitHub MCP `actions_list` (`list_workflow_runs`, filter the branch) or `gh run list -b <branch>`.
2. Do not poll in a loop: start a background timer (`sleep 300` with `run_in_background`) and check when it ends;
   build+unit takes ~3-5 min, emulator parts ~5-6 more.
3. Read results: `list_workflow_jobs` for each job's conclusion; for a failure, get the run's log zip URL
   (`actions_get` `get_workflow_run_logs_url`), `curl -L -o logs.zip`, unzip into a scratch folder, and grep
   (`FAIL:|SMOKE RESULT|FATAL EXCEPTION|error:|Caused by`). Details: `references/triage.md`.
4. Tell the user the outcome plainly: green or the failing check, the cause, the fix, and for a release the link
   and build number.

## Starting CI in a new repo

1. Copy the matching workflow from `assets/` (Android: `android-build.yml`; Node: `node-ci.yml`; Python:
   `python-ci.yml`) to `.github/workflows/build.yml`; set the branches (`main`, `claude/**`), the paths that need
   the expensive stage, the artifact names.
2. Copy `release_notes.py` (and for Android `test_failures.py`, `settle.sh`, `smoke-lib.sh`, `find_text.py`,
   `describe_ui.py`, `ascii_shot.py`) into `ci/`.
3. Add `CHANGELOG.md` with `## <version> — <date>` sections and the version file (`gradle.properties`
   `app.versionName=`, `package.json` `version`, `pyproject.toml`).
4. Push once with `[smoke]` to warm the caches and save the emulator snapshot; later pushes start warm.
