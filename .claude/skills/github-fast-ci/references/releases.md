# Releases: one source of truth, published only when everything passed

Contents: 1 The scheme · 2 CHANGELOG.md · 3 The marker · 4 One asset per release · 5 The app side · 6 Branches and
merging

## 1. The scheme

- The version lives in one file (`gradle.properties` `app.versionName=0.15.3`, `package.json`, `pyproject.toml`).
- The build number is `github.run_number` (`versionCode` on Android): every build installs over the previous one,
  and the build number in the asset's name tells the in-app updater whether it is newer.
- Tag `v<version>`; release title `<App> <version>`; `target_commitish: ${{ github.sha }}`; `make_latest: true`.
- Release notes = that version's section of `CHANGELOG.md` (`ci/release_notes.py <version>`), plus one line with the
  build number and commit.
- Publishing waits for every test job (`needs: [build, smoke]`), and a release always runs the slow tests.

## 2. CHANGELOG.md

```markdown
# Changelog

## 0.15.3 — 2026-11-23

### Added
- Board photos with the launcher's own camera, several in a row. … (Settings › Study companion › More)

### Removed
- The unlock animation (0.15.0 to 0.15.2): it played only once the phone was already open.

## 0.15.2 — 2026-11-22
…
```

- `## <version> — <date>`, then `### Added`, `### Changed`, `### Removed`, `### Fixed`, items as `- ` lines in plain
  words for the user (what changed, where it is). Wrapped lines indented.
- A version that was built but not released alone: `*Not published on its own: its changes came with 0.x.y.*` right
  under its heading.
- The release job checks the section exists **before building** (`release_notes.py` exits 1 when it does not), so a
  forgotten entry fails fast instead of publishing empty notes.

## 3. The marker

- Publish only when the head commit's message contains `[release]` (or a manual run with the `release` input).
- The whole message counts, body included: **never write the marker in any other commit message** (describing the
  CI rules in a commit body once published a release). Same for `[smoke]` where it matters.
- Never push a release without the user's go-ahead; when asked to "publish", that means a `[release]` commit.
- Do not push anything else to the branch while the release run is going (`cancel-in-progress` would cancel it).
  Commit locally and push after the publish job is green.
- After pushing: wait for the run, check the publish job succeeded, then give the user the release link and the
  build number.
- If the release run fails on a flaky check, fix the cause (often the test), and release the same version again: a
  newer build replaces the asset in the existing release.

## 4. One asset per release

Before uploading, delete the release's existing assets (`gh release view "$TAG" --json assets --jq '.assets[].name'`
then `gh release delete-asset "$TAG" "$name" --yes`), so a re-release of the same version leaves exactly one file
(the newest build).

## 5. The app side

- CI copies `CHANGELOG.md` into the app (`app/src/main/assets/`): after an update the app shows that version's
  section once ("What's new"), and its Updates screen shows the notes before installing.
- The in-app updater reads GitHub's "latest release", compares the build number in the asset's name with its own,
  downloads and hands the APK to the installer (same signing key, so it installs over and keeps everything).

## 6. Branches and merging

- Work and release from the session's branch (`claude/**`); CI builds those branches.
- When merging a release branch into `main`, use a **merge commit** (not squash or rebase): a squash's message would
  contain the `[release]` lines of its commits and publish again.
