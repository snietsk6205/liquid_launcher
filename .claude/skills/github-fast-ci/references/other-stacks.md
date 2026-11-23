# Other stacks: the same rules, other tools

Templates: `../assets/node-ci.yml` (Node, web, Playwright, GitHub Pages), `../assets/python-ci.yml` (uv / pip).

## Caching that pays

| Stack | Cache | Key |
|---|---|---|
| Node (npm) | `actions/setup-node` with `cache: npm` | `package-lock.json` (automatic) |
| Node (pnpm) | `pnpm/action-setup` then `setup-node` with `cache: pnpm` | `pnpm-lock.yaml` |
| Playwright | `~/.cache/ms-playwright` via `actions/cache`; install browsers only on a miss | the lockfile (Playwright's version) |
| Python (uv) | `astral-sh/setup-uv` with `enable-cache: true` | `uv.lock` |
| Python (pip) | `actions/setup-python` with `cache: pip` | `requirements*.txt` |
| Gradle | `gradle/actions/setup-gradle` with `cache-read-only: false` | automatic |
| Flutter | `subosito/flutter-action` with `cache: true` (+ Gradle as above for Android) | Flutter version |
| Docker | `docker/setup-buildx-action` + `docker/build-push-action` with `cache-from: type=gha`, `cache-to: type=gha,mode=max` | layers |
| Anything else | `actions/cache` on the tool's download folder | `hashFiles('<lockfile>')` + a `-v1` suffix to reset |

- Install with the lockfile and offline-first flags (`npm ci --prefer-offline --no-audit --no-fund`,
  `uv sync --frozen`): faster and reproducible.
- A cache entry is immutable per key: bump a version suffix when its content must change.
- Caches are scoped per branch with fallback to the default branch: a first push on a new branch reads main's.

## Stages

1. Every push: install (cached), lint, unit tests, build; upload the build output once as an artifact.
2. On `[smoke]`, risky paths or a release: end-to-end tests against that same artifact (Playwright with
   `--reporter=line --trace off` unless debugging a failure; upload `test-results/` with `if: always()`).
3. On `[release]`: release notes from CHANGELOG.md, an archive of the build as the release asset, optional deploy
   (GitHub Pages: `upload-pages-artifact` + `deploy-pages`, with `pages: write` and `id-token: write` only on that
   job).

## Static sites and apps without a store

- The release asset is the build (zip of `dist/`), named `<name>-<version>-build<run>.zip`.
- Deploy previews per branch cost minutes; deploy only from releases (or `main`) unless a preview is asked for.

## Other speed tips

- Path filters at the workflow level (`on.push.paths-ignore: ['**.md', 'docs/**']`) skip runs for documentation;
  the in-job `git diff` check (Android template) is finer when some jobs should still run.
- `timeout-minutes` on every job: a hung job otherwise burns up to 6 hours.
- Matrix only where it buys coverage you need; each leg costs full minutes.
- `concurrency` with `cancel-in-progress: true` per branch, and nothing pushed during a release run.
- Action versions: use the current major version of each action (checkout, setup-*, cache, upload-artifact); pin
  third-party actions to a major version tag at least, or a commit SHA for anything that gets secrets.
