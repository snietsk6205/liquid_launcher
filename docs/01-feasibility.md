# 1. Feasibility review

Device: HONOR 90 Lite, MagicOS on Android 13 (API 33), no root, locked bootloader.
Architecture: HONOR Home stays the default launcher; Liquid Launcher is a normal app that its own AccessibilityService brings to the front.

Legend: **OK** = works, low risk. **TEST** = should work, must be confirmed on your phone (Phase 1A does this). **LIMIT** = works only partly, trade-off described. **CUT** = not doing it, reason given.

## 1.1 Verdict in one paragraph

The whole plan is feasible without root. The redirect trick is the only part the rest depends on, and you have already proven it works with MacroDroid; doing it natively will be faster than MacroDroid because the service filters events to one package and the launcher process stays warm. Three things cannot be matched to a real default launcher and never will be: deep shortcuts are partial, the app-close animation cannot fly back into your icon, and pressing Home while already on the launcher briefly passes through HONOR Home. Liquid glass over the wallpaper works only if the launcher draws the wallpaper itself, which changes how the wallpaper is set. Everything else is ordinary launcher engineering.

## 1.2 The redirect engine (replaces the three MacroDroid macros)

| Topic | Status | Notes |
|---|---|---|
| Starting an activity from the background | **TEST** | Android blocks background activity starts, but an app whose AccessibilityService is bound by the system is exempt. MacroDroid relies on the same rule. Fallback if MagicOS is stricter: the "Display over other apps" permission, which is a second documented exemption. |
| Detecting "HONOR Home is in front" | **OK** | `TYPE_WINDOW_STATE_CHANGED` from package `com.hihonor.android.launcher`, class `…drawer.DrawerLauncher`. |
| Detecting "recents is open" | **TEST** | Look for view id `overview_panel_launcher3` in the active window. Needs `canRetrieveWindowContent` and `flagReportViewIds`. |
| Surviving a MagicOS update that renames ids | **OK** | Detection is data-driven, see 1.2.2. |
| Battery cost | **OK** | The service subscribes to events from two packages only (HONOR Home and itself). While you are in any other app it receives nothing and does not wake. Content-change events are subscribed only while HONOR Home is in front. |
| Surviving reboot | **OK** | Android re-binds enabled accessibility services after boot without any receiver. |
| Surviving app kill | **TEST** | Android re-binds after a crash. HONOR's power manager can kill and effectively disable the service unless the app is set to "manage manually" in App launch and unrestricted in Battery. Onboarding walks through both, and the home screen shows a banner if the service is not connected. |
| Enabling the service on a sideloaded app | **TEST** | Android 13 hides accessibility and notification access for sideloaded apps behind "Allow restricted settings" (App info → ⋮). One-time step, covered in onboarding. |

### 1.2.1 How it works

A small state machine in the service:

```
OTHER_APP ──HONOR Home window appears──► HOME_SEEN
HOME_SEEN ──recents signature found────► RECENTS
HOME_SEEN ──no signature───────────────► REDIRECT (delay A, default 50 ms, recents re-checked when it ends)
RECENTS   ──signature gone, HONOR Home still in front──► REDIRECT (delay B, default 80 ms)
RECENTS   ──another app comes to front─► OTHER_APP
screen off ──delay C (default 1000 ms)─► REDIRECT (pre-positions the launcher behind the lock screen)
```

`REDIRECT` brings the existing launcher task to the front with no window animation. The activity is `singleTask`, excluded from recents, and already alive, so this is a hot start (a few frames), not a cold start.

### 1.2.2 Robust detection

- Recents signatures are a list, not one constant: exact ids (`overview_panel_launcher3`, `overview_panel`), an id substring rule (`overview_panel`), and node class names (`RecentsView`, `TaskView`). Any match counts.
- **Calibration wizard**: the app asks you to open recents once, snapshots the view tree of HONOR Home in both states, and stores the ids that exist only in recents. Ten seconds, and only needed if the built-in signatures stop matching.
- When the HONOR Home package version changes, the detector marks itself "unverified" and re-checks its signatures the next time HONOR Home is in front; if none of the known ids exist anywhere in the tree, you get one notification offering calibration.
- **Loop guard**: more than 4 redirects in 3 seconds pauses the engine for 30 seconds and writes the reason to the debug log. This prevents the worst failure, a phone where recents can never be opened.
- A Quick Settings tile and a launcher action toggle the engine, and "Open HONOR Home" pauses it for a chosen time so you can still reach HONOR's own settings.

### 1.2.3 "No visible flash" — what is and is not possible

| Situation | Result |
|---|---|
| Closing an app with the home gesture | During the swipe the system shows HONOR Home under the shrinking app. This cannot be intercepted. **Fix: keep HONOR Home in drawer style with an empty home screen and empty dock**, so what shows is only wallpaper. Then the launcher content settles in over the identical wallpaper and the hand-over is not noticeable. |
| Leaving recents to home | Same as above, after delay B. |
| Unlocking the phone | No flash: the launcher was already placed in front at screen-off. |
| Pressing Home while already on the launcher | **LIMIT.** To the system the launcher is just an app, so it plays an app-close animation into HONOR Home, then the redirect brings the launcher back. An optional accessibility overlay showing a snapshot of the home screen can cover the gap; I will test whether it looks better or worse than the plain cut. |
| App-close animation flying back into the icon | **CUT.** That animation belongs to the default launcher. Apps close toward HONOR's empty home. App-open animations from the icon work normally. |

## 1.3 Limits of not being the default launcher

| Feature | Status | Plan |
|---|---|---|
| Listing and launching apps, including work profile | **TEST** | `LauncherApps.getActivityList/startMainActivity` work for profiles in your own profile group without the launcher role on Android 13. Phase 1A diagnostics confirm it with Island. |
| Deep shortcuts (long-press) | **LIMIT** | `LauncherApps.getShortcuts` is refused for non-default launchers. Replacement, in order: (1) static shortcuts parsed from the app's manifest, shown when their target activity is exported (many are, some are not); (2) legacy `CREATE_SHORTCUT` providers (direct dial, settings pages, etc.); (3) an activity picker like Nova's "Activities"; (4) the app's live notifications and their actions in the popup. Dynamic shortcuts (e.g. a specific chat) are not available. |
| "Add to home screen" from other apps | **CUT** | Pin requests are delivered to the default launcher only. Use the launcher's own pickers instead. |
| Work profile pause/resume | **LIMIT** | Needs the launcher role. The launcher shows the paused state and the button opens Island (or system settings) as you proposed. |
| AppWidgets | **OK** | Standard bind flow: the first widget from each provider shows one system permission dialog. |
| Notification badges | **OK** | NotificationListenerService, optional. Needs the restricted-settings step. |
| Lock screen, notification shade, recents, power menu, screenshot as gesture actions | **OK** | The accessibility service can trigger these global actions. Locking this way keeps fingerprint unlock working. |
| Recents showing the launcher as a card | **TEST** | The task is excluded from recents; whether MagicOS still shows it while it is the top task needs a look on the device. |

## 1.4 Liquid glass and the wallpaper

Verified from the library source (`io.github.kyant0:backdrop` 2.0.1, Apache-2.0):

- Refraction (`lens`) needs RuntimeShader, API 33. Blur needs API 31. minSdk 33 covers both.
- The glass samples a **backdrop made of the app's own drawing** (a recorded Compose layer or a canvas callback). It cannot sample the system wallpaper behind a transparent window.
- Android 13 no longer lets a normal app read the current wallpaper image (`WallpaperManager.getDrawable` returns the default wallpaper unless the app holds All-files access).

Consequence and plan:

| Wallpaper mode | How | Glass | Trade-off |
|---|---|---|---|
| **Launcher-managed (default)** | You pick the image in the launcher. It crops to screen size, sets it as the system wallpaper, and keeps the same bitmap to draw itself. | Full refraction of wallpaper and icons | Changing wallpaper elsewhere needs a re-pick (or the option below). No live wallpapers. |
| System + All-files access | Launcher reads the system wallpaper using the All-files permission. | Full | A broad permission; also enables file search. |
| System, transparent window | Real wallpaper shows through. | Frosted tint only (no refraction of the wallpaper) | Live wallpapers work. |

Performance on the Dimensity 6020 at 90 Hz is the real risk, not the API. Rules: glass only on a few surfaces at a time (search pill, dock, folder, cards), never per-icon in a scrolling list; static backdrops are cached; three quality tiers (Full / Frosted / Tint) with automatic step-down when frame time is exceeded. **TEST** in Phase 1A with a glass benchmark page.

Wallpaper-aware colors work without any permission (`WallpaperColors`, plus our own palette extraction from the managed bitmap).

## 1.5 Feature-by-feature

| Feature | Status | Notes |
|---|---|---|
| Free grids up to 12 × 12, per home/dock/folder | **OK** | |
| Icon packs (global, home, drawer, per-app), auto-masking | **OK** | Standard `appfilter.xml` packs incl. iconback/iconmask/iconupon/scale, calendar icons, themed monochrome icons. |
| Per-icon swipe up/down and double-tap | **OK** | Double-tap adds a ~200 ms tap delay only on icons that have one configured. |
| Folders with shapes, glass, preview styles, tap-first/swipe-open | **OK** | |
| Gestures: swipes, double tap, two-finger, pinch, long press | **OK** | |
| Swipe from left/right screen edges | **LIMIT** | Collides with the system back gesture. Android allows an app to claim at most 200 dp of edge height per side, so edge gestures get a configurable zone, not the whole edge. |
| Swipe down for search vs. notification shade | **OK** | Pulling from the status bar still opens the shade. A two-finger swipe down can open it too. |
| Universal search: apps, tags, folders, settings, calculator, units, bangs, web | **OK** | In-memory index, sub-millisecond on a few hundred apps. |
| Search: contacts, calendar | **OK** | One permission each, optional. |
| Search: files | **LIMIT** | Media files via MediaStore permissions. Documents and arbitrary files need All-files access. Optional. |
| Drawer: tags, custom tabs, sorting, hide, rename, bottom-up fill | **OK** | |
| Calendar study blocks | **OK** | `CalendarContract`, read-only by default. |
| Tasks via GitHub | **OK** | Improved design in doc 5 (two files, one writer each). |
| Study mode, profiles | **OK** | Evaluated when the launcher becomes visible, so no alarms or background work. |
| Mindful friction | **LIMIT** | Default: only for launches made from the launcher. Optional: also for launches from recents/notifications, which requires the service to listen to all apps (slightly more battery). |
| Predicted/recent apps | **OK** | Based on launches through the launcher. Optional Usage Access permission for the full picture. |
| Weather in search bar/widgets | **LIMIT** | Needs network and a location or fixed city. Off by default (offline-first); Open-Meteo, no account. |
| Media controls widget | **OK** | Uses notification access. |
| Backup/restore to file and GitHub | **OK** | |
| Undo for layout edits | **OK** | |
| Baseline profiles | **OK** | Generated on an emulator in GitHub Actions at the end of each phase, not on every push. |
| Material 3 Expressive motion | **OK** | Spring-based motion scheme tokens, one global speed multiplier plus per-element overrides. |
| In-app updater | **OK** | Reads the newest GitHub release and installs it. Session-based installs also avoid the restricted-settings step on updates. |

## 1.6 Cut or postponed, with reasons

| Item | Decision | Reason |
|---|---|---|
| Shizuku-based shortcuts / quiet mode | Postponed, probably never | Needs wireless debugging re-paired after each reboot. Conflicts with "nothing should require my time". |
| Live wallpapers with full glass | Cut | Not readable by apps. Available with frosted tint only. |
| Landscape and tablet layouts | Postponed | Phone, portrait first. Layout model keeps it possible. |
| Plugin/scripting system | Postponed | Large surface, low value for one user. The text-template widget covers most "custom" needs. |
| Online web-search suggestions | Postponed | Network on every keystroke contradicts offline-first. Bangs and "search the web for …" cover it. |
| Google Tasks instead of GitHub | Rejected | Needs a Google Cloud project and OAuth on the device; Claude has no Tasks connector here. |
| "Every customization imaginable" on day one | Staged | The settings registry (doc 3) makes adding an option a few lines, so options grow each phase rather than being promised up front. |

## 1.7 Third-party code

| Project | License (verified) | Use |
|---|---|---|
| Kyant0/AndroidLiquidGlass (`backdrop`) | Apache-2.0 | Dependency for glass surfaces. |
| Chethan616/Artefact | MIT or Apache-2.0 | Reference for glass components and Expressive motion. Any copied code is credited in `NOTICE`. |
| wasi-master/wmkeyboard | MIT | Reference for the layered module structure, DataStore settings repository, theme spec and file export. Its theme file format is not in the README, so I will read the source before reusing anything. |
