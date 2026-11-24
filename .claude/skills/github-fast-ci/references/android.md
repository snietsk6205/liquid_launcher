# Android CI: Gradle, the emulator test, the script

Contents: 1 The build job · 2 When the emulator runs · 3 Emulator caching · 4 Parallel parts · 5 Writing the test
script · 6 Timings and what moved them · 7 Gotchas

Template: `../assets/android-build.yml`; helpers: `../assets/smoke-lib.sh`, `settle.sh`, `find_text.py`,
`describe_ui.py`, `ascii_shot.py`, `test_failures.py`, `release_notes.py`.

## 1. The build job

- `actions/setup-java` (Temurin 21) + `gradle/actions/setup-gradle` with **`cache-read-only: false`** (otherwise only
  the default branch writes the cache and every `claude/**` branch builds cold).
- One Gradle call for everything: `./gradlew --quiet --console=plain --continue testDebugUnitTest :app:assembleRelease
  -PversionCode=${{ github.run_number }}`. `--continue` reports every failing module in one run; `--quiet` keeps the
  log readable.
- On failure, `ci/test_failures.py` prints each failed test with the first lines of its message (the XML reports
  are otherwise only in an artifact), and the reports are uploaded.
- The APK is renamed `<App>-<version>-build<run>.apk` and uploaded as an artifact the emulator jobs download (build
  once, test the same file).
- Copy `CHANGELOG.md` into `app/src/main/assets/` before building: the app's What's new reads it.

## 2. When the emulator runs

The emulator test is the slow part, so it runs only when it can catch something: `[release]` (always), `[smoke]`
in the message, manual runs, and pushes that changed risky paths (`git diff --name-only $before $after | grep -qE
'^(feature/redirect/|ci/|\.github/workflows/)'`). A new or force-pushed branch runs it (the diff is unknown). Put
`[smoke]` in the message when a UI or behaviour change needs it.

## 3. Emulator caching

- `actions/cache` for the **system image** (`/usr/local/lib/android/sdk/system-images/android-34/google_apis/x86_64`,
  key `sysimg-34-google_apis-x86_64-v1`): no download.
- `actions/cache` for the **AVD with a snapshot** (`~/.android/avd/*`, `~/.android/adb*`, key
  `avd-34-google_apis-x86_64-vN`). On a miss, one step boots the emulator and runs `ci/settle.sh 300` (waits until
  the CPU is calm three readings in a row, below 40 %), and the runner action saves the snapshot when it stops. The
  test step boots from it with `-no-snapshot-save` (read-only, so the cached state never drifts).
- **Save the snapshot when the phone is calm**: v1 was saved mid first-boot work and v2 after a blind 30 s; both
  made the app ANR in the first minute of each run (Android still delivers BOOT_COMPLETED to every app and Play
  services start). Bump the key's version to start anew; if "ANR in" comes back, drop the snapshot cache and boot
  cold + settle each run (keep the image cache).
- `cores: 2`: without a GPU, swiftshader draws on the same CPUs, and 4 cores were no faster (D187). A snapshot only
  loads with the core count it was saved with, so changing cores needs a new key.
- `-gpu swiftshader_indirect -no-window -noaudio -no-boot-anim -camera-back none`; `disable-animations: false` when
  the test checks animations end and screens go still.
- API 34 google_apis x86_64 with KVM enabled (the udev rule step). The API 35 image refuses to enable an
  accessibility service from adb.

## 4. Parallel parts

Split the script into parts by what the checks share (checks that use the same test data stay together), run them
as a matrix (`part: [redirect, drawer, study]`) with `fail-fast: false`; each part repeats the common start
(install, permissions, first launch, warm-up). Three parts of ~4-6 minutes replace one of 12-13. The release job
needs all parts.

## 5. Writing the test script

`ci/smoke.sh <part>` (sourcing `smoke-lib.sh`):
- **Wait for the thing, never sleep for it**: `wait_screen name "text" 8`, `wait_log "pattern" 6`, `wait_top
  "Activity" 6`, `wait_until 6 some_check`. Each wait that took 5 s or more, or gave up, is listed at the end
  ("SLOW WAITS"), so slow spots are visible without timing anything by hand. About 250 s of fixed sleeps were the
  biggest single cost before this.
- **Read the app's own log** (`adb logcat -v time -s Tag:I > $LOG &` as a stream): the app logs plain sentences
  about what it decided; checks can wait for them and failures can print them.
- **Print what was on screen when a check fails**: `look name` = an ASCII rendering of the screenshot plus the
  list of on-screen texts with their positions, right in the log (no artifact download needed to see it).
- **Tap by text**, not coordinates: `tap_text name "Done" exact` (finds the node in the uiautomator dump).
- **End with the summary**: crash check (`FATAL EXCEPTION` for the package in logcat), the app log's last lines,
  time per section, slow waits, `SMOKE RESULT (part): all checks passed | N check(s) failed: - …`, exit code = the
  number of failures. Every check runs even after one fails.
- **Idle screens**: count frames drawn in 3 s while a screen sits still (`dumpsys gfxinfo`), so an animation that
  never stops fails the test.
- **Robust input**: `adb shell input swipe` on a slow emulator arrives as a few points (its last move dropped), so a
  fast swipe can read as short and slow. Prefer 450 ms swipes; when the app's log says the gesture did not do its
  job (e.g. "released at 0.40: closes"), swipe again, slower. Log what each release decided so the test can tell.
- A dialog "not responding" can appear on a slow emulator: detect and dismiss it, note it, and print the ANR trace
  from `/data/anr` (root on google_apis images) for the record.
- Shell-started activities (`adb shell su 0 am start -n pkg/.Activity`) land in whatever task has their affinity;
  check what matters ("the camera is gone"), not which activity is next.

## 6. Timings and what moved them (Liquid Launcher)

| Change | Effect |
|---|---|
| Emulator only on `[smoke]` / `[release]` / risky paths | most pushes: ~3-5 min instead of ~16 |
| Gradle cache on every branch | build + unit tests in ~2.5-4 min on a warm branch (5-6 when dependencies change) |
| System image cache + calm AVD snapshot | boot in ~12 s instead of a download and a cold boot |
| Waits instead of fixed sleeps (about 250 s of them) | the test's own time down by minutes, and less flaky |
| Three parallel parts | ~4-6 min instead of 12-13 |
| A release | about 10 min end to end (build 2.5-5, parts in parallel 5-6, publish < 1) |

## 7. Gotchas

- Runners vary 2-3x in speed: log `nproc` and the CPU model in each job to compare timings fairly.
- The emulator draws in software: never read performance from it (the app's frame times there are meaningless).
- An app crash in `onCreate` shows up as a failed screen check plus a `FATAL EXCEPTION` in logcat: read the
  `Caused by` line first (e.g. `Window.getInsetsController()` before the decor view existed).
- Accessibility services on Android 15 need "restricted settings" allowed: install with `-i com.android.vending`
  and `appops set <pkg> ACCESS_RESTRICTED_SETTINGS allow`.
