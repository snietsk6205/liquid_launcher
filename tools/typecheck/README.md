# Local type check (no Android SDK needed)

Google's Maven (dl.google.com) is blocked in the cloud sessions, so Gradle cannot build here; every real build is a CI
run. This harness catches most mistakes in seconds instead: it compiles every Kotlin source of the project with
Kotlin 2.4.10 and the Compose compiler plugin against JetBrains' Compose **desktop** 1.8.2, Robolectric's
`android-all` jar, the backdrop library's desktop jar and the small stubs in `stubs/` (androidx.core, activity,
datastore, `LocalContext`, the R class…).

```bash
tools/typecheck/fetch.sh      # once per session: downloads the jars from Maven Central into lib/ and kc/ (retries)
tools/typecheck/tc.sh         # whole project; prints "N errors, M new": only the M new ones matter
tools/typecheck/ut.sh         # compiles core/common, core/settings, core/data, feature/redirect + tests, runs JUnit
tools/typecheck/ut.sh SettingsMapTest ThemeStoreTest   # only these tests
```

- `baseline.txt` holds the 14 errors that are gaps of the desktop APIs (dynamic colour schemes, a few Android-only
  overloads), not bugs. `tc.sh baseline` records a new baseline; do that only when the known gaps change.
- `modlint.py` (run by `tc.sh`) checks module boundaries, which a one-pass compile cannot see.
- `gen_r.sh` regenerates `stubs/RStub.kt` when drawables are added to core/designsystem.
- A clean result here is not a build: resources, manifests, AGP and Android-only APIs are only checked by CI.
