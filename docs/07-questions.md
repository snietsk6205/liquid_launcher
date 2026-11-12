# 7. Questions

None of these block Phase 1A. Where you do not answer, I use the default in the last column and record it in `DECISIONS.md`.

## Answers that change the design

| # | Question | Default if no answer |
|---|---|---|
| 1 | **HONOR Home**: can you keep it in drawer style with an empty home screen and empty dock? This is what makes the hand-over look like plain wallpaper. | Assume yes |
| 2 | **Wallpaper**: is it fine that you pick the wallpaper inside the launcher (it sets the system wallpaper too)? This is what allows real glass refraction. Do you use live wallpapers? | Launcher-managed, no live wallpapers |
| 3 | **Screen off**: your macro shows the launcher after every screen-off, even if you were inside an app. Keep that, or only when you were on home? | Keep as is (always), option in settings |
| 4 | **Navigation**: gesture navigation or three buttons? | Gestures |
| 5 | **Study sync repo**: will you create a private repo (suggested name `study-sync`) and a fine-grained token for it when Phase 5 arrives? I cannot create repos from here. | Yes, at Phase 5 |
| 6 | **Which Claude writes the plan**: a scheduled Claude task each morning, or you ask in chat? This decides how `plan.json` is produced. | Scheduled each morning, plus on request |
| 7 | **Study calendar**: are study blocks in their own Google calendar, or mixed with other events? If mixed, how are they named? | Assume a dedicated calendar; fall back to a description marker |

## Preferences

| # | Question | Default if no answer |
|---|---|---|
| 8 | Which icon pack do you use now (the thin line icons)? I will test masking against it. | Test with a generic line pack |
| 9 | Which font is your clock (the handwritten one)? If it is a file you own, I can bundle it for you. | A similar open-licensed handwritten font |
| 10 | Double tap on empty space: lock screen? | Yes |
| 11 | Dock: your current layout has none. Keep it off by default? | Off |
| 12 | Labels under icons: off on home, on in drawer? | Yes |
| 13 | Which apps or folders are "distracting" (Anime, Video, …) and which are study apps? A rough list is enough to pre-fill tags. | You tag them in Phase 4 |
| 14 | Web search engine and the bangs you want (`!yt`, `!g`, `!w`, …)? | DuckDuckGo; `!yt !g !w !gh !maps` |
| 15 | Settings language: English only, or also another language? | English; layouts kept translation-ready |
| 16 | Weather: do you want it at all (needs network and a city)? | Built later, off by default |
| 17 | Do you want launch friction to also apply when a distracting app is opened from recents or a notification? It costs a little battery. | Launcher launches only |

## Practical

| # | Question | Default if no answer |
|---|---|---|
| 18 | **Signing key**: to let every new APK install over the old one, the signing key must stay the same. Simplest zero-effort option is a dedicated key committed to this private repo. Acceptable? The alternative is you adding it once as a GitHub Actions secret. | Key in the private repo |
| 19 | **App name and package**: "Liquid Launcher", `dev.liquid.launcher`? Changing the package later means a reinstall. | As suggested |
| 20 | **Releases**: APKs on this repo's Releases page (you open the link on the phone, logged in to GitHub). OK? | Yes |
| 21 | Is Island your only work-profile manager, and roughly how many apps live there? | Island; test with whatever is there |
