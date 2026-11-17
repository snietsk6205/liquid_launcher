#!/usr/bin/env bash
# End-to-end check on an emulator: installs the release APK, enables the accessibility
# service and verifies the redirect against the emulator's launcher (same QuickStep base as
# HONOR Home): home redirects, recents is left alone, leaving recents redirects.
set -u

PKG=dev.liquid.launcher
HOME_ACTIVITY="$PKG/.MainActivity"
SETTINGS_ACTIVITY="$PKG/dev.liquid.launcher.feature.settings.SettingsActivity"
SERVICE="$PKG/dev.liquid.launcher.feature.redirect.LauncherAccessibilityService"
OUT=out/smoke
LOG="$OUT/launcher.log"
FAILS=0
mkdir -p "$OUT"

APK=$(ls out/*.apk | head -1)
echo "== Installing $APK"
# Android 15 refuses accessibility for apps with no installer ("restricted settings"), so
# install as if from a store and allow restricted settings, like the user does by hand.
adb install -r -i com.android.vending "$APK" || adb install -r "$APK" || { echo "FAIL: install"; exit 1; }
adb shell appops set "$PKG" ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null || true

# The launcher's own log lines, captured as a stream so nothing is lost to buffer rotation.
adb logcat -c
adb logcat -v time -s LiquidLauncher:I > "$LOG" 2>/dev/null &
LOGCAT_PID=$!

top_activity() {
  adb shell dumpsys activity activities 2>/dev/null | grep -E 'topResumedActivity|mResumedActivity' | head -1
}

# wait_top <pattern> <seconds> : succeeds once the resumed activity matches.
wait_top() {
  local pattern=$1 seconds=$2 i
  for i in $(seq 1 $((seconds * 4))); do
    if top_activity | grep -q "$pattern"; then return 0; fi
    sleep 0.25
  done
  return 1
}

# wait_log <pattern> <seconds> : succeeds once the launcher log contains the pattern.
wait_log() {
  local pattern=$1 seconds=$2 i
  for i in $(seq 1 $((seconds * 2))); do
    if grep -q "$pattern" "$LOG"; then return 0; fi
    sleep 0.5
  done
  return 1
}

ANR_DIALOGS=0

# A slow emulator (software GPU on a busy runner) can miss the system's 5-second input
# timeout, and the "isn't responding" dialog then covers the screen for every later step.
# Dismiss it with "Wait", as a user would, and count it; crashes are still checked strictly.
clear_anr() {
  local i pos
  for i in 1 2 3; do
    adb shell rm -f /sdcard/anr.xml >/dev/null 2>&1
    adb shell uiautomator dump /sdcard/anr.xml >/dev/null 2>&1
    adb exec-out cat /sdcard/anr.xml > "$OUT/anr-check.xml" 2>/dev/null || return 0
    if grep -q "isn&apos;t responding\|isn't responding" "$OUT/anr-check.xml"; then
      pos=$(python3 ci/find_text.py "$OUT/anr-check.xml" "Wait" exact) || return 0
      echo "      (dismissed a 'not responding' dialog: the emulator is running slowly)"
      ANR_DIALOGS=$((ANR_DIALOGS + 1))
      adb shell input tap $pos
      sleep 2
    else
      return 0
    fi
  done
}

pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; echo "      top: $(top_activity)"; FAILS=$((FAILS + 1)); }
redirect_count() { grep -c 'REDIRECT (' "$LOG" || true; }

service_up() {
  [ "$(grep -c 'service connected' "$LOG")" -gt "$(grep -c 'service disconnected' "$LOG")" ]
}

# peek <name> : screenshot and a rough character thumbnail.
peek() {
  adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || true
  echo "-- screen: $1"
  python3 -W ignore ci/ascii_shot.py "$OUT/$1.png"
}

# texts <name> [lines] : the texts on screen. Dumping the screen makes Android suspend every
# accessibility service for a moment, so wait until ours is back.
texts() {
  adb shell rm -f /sdcard/ui.xml >/dev/null 2>&1
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/ui.xml > "$OUT/$1.xml" 2>/dev/null || true
  python3 ci/describe_ui.py "$OUT/$1.xml" "${2:-40}"
  local i
  for i in $(seq 1 40); do service_up && return 0; sleep 0.5; done
  echo "NOTE: accessibility service did not come back after the screen dump"
}

# look <name> [lines] : thumbnail and texts.
look() {
  peek "$1"
  texts "$1" "${2:-40}"
}

focus() { adb shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus' | head -1 | sed 's/^ *//'; }

echo "== Default launcher"
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
adb shell wm dismiss-keyguard || true
adb shell input keyevent KEYCODE_HOME
sleep 2

echo "== Enabling the accessibility service"
adb shell settings put secure enabled_accessibility_services "$SERVICE"
adb shell settings put secure accessibility_enabled 1
if wait_log 'service connected' 60; then
  pass "accessibility service connected"
else
  fail "accessibility service connected"
  echo "      enabled services: $(adb shell settings get secure enabled_accessibility_services)"
  adb logcat -d 2>/dev/null | grep -iE "accessib|restricted|EnhancedConfirmation" | tail -25 | sed 's/^/        /'
fi
sleep 2

echo "== 1. App starts"
adb shell am start -n "$HOME_ACTIVITY" >/dev/null
if wait_top "$HOME_ACTIVITY" 15; then pass "launcher activity starts"; else fail "launcher activity starts"; fi
sleep 4
look 01-home 30

echo "== 2. Home button while on the launcher returns to the launcher"
# A freshly booted emulator sometimes shows "Pixel Launcher isn't responding"; that dialog
# is the emulator's, not ours, so close system dialogs and allow one retry.
close_dialogs() {
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1
  adb shell dumpsys window 2>/dev/null | grep -q "Application Not Responding" && adb shell input keyevent KEYCODE_ENTER
  true
}
home_press_check() {
  close_dialogs
  adb shell am start -n "$HOME_ACTIVITY" >/dev/null
  wait_top "$HOME_ACTIVITY" 8 || true
  sleep 2
  BEFORE=$(redirect_count)
  adb shell input keyevent KEYCODE_HOME
  # Wait for the redirect itself (a slow emulator can take several seconds to show its launcher).
  for i in $(seq 1 20); do
    sleep 0.5
    AFTER=$(redirect_count)
    [ "$AFTER" -gt "$BEFORE" ] && break
  done
  wait_top "$HOME_ACTIVITY" 6 && [ "$AFTER" -gt "$BEFORE" ]
}
if home_press_check; then
  pass "home press redirected ($BEFORE -> $AFTER)"
elif echo "      retrying once after closing system dialogs" && home_press_check; then
  pass "home press redirected on retry ($BEFORE -> $AFTER)"
else
  fail "home press redirected ($BEFORE -> $AFTER)"
fi

echo "== 3. Going home from another app redirects"
adb shell am start -a android.settings.SETTINGS >/dev/null
wait_top "com.android.settings" 10 || echo "note: system settings did not open"
sleep 2
BEFORE=$(redirect_count)
adb shell input keyevent KEYCODE_HOME
if wait_top "$HOME_ACTIVITY" 6; then pass "app -> home redirected"; else fail "app -> home redirected"; fi
sleep 1
AFTER=$(redirect_count)
[ "$AFTER" -gt "$BEFORE" ] || fail "redirect was logged ($BEFORE -> $AFTER)"

echo "== 4. Recents opened from an app is not interrupted"
adb shell am start -a android.settings.SETTINGS >/dev/null
wait_top "com.android.settings" 10 || true
sleep 1
adb shell am start -a android.intent.action.DIAL >/dev/null 2>&1
sleep 3
echo "      front app: $(top_activity)"
BEFORE=$(redirect_count)
adb shell input keyevent KEYCODE_APP_SWITCH
sleep 5
AFTER=$(redirect_count)
if top_activity | grep -q "$HOME_ACTIVITY"; then
  fail "recents stayed open (launcher came to the front)"
elif [ "$AFTER" -ne "$BEFORE" ]; then
  fail "no redirect while recents is open ($BEFORE -> $AFTER)"
else
  pass "recents stayed open"
fi

echo "== 4b. Scrolling inside recents"
# Scroll to the previous card: the app that was shown live is replaced by a snapshot.
adb shell input swipe 250 1200 850 1200 400
sleep 5
AFTER=$(redirect_count)
echo "      top:   $(top_activity)"
echo "      focus: $(focus)"
if top_activity | grep -q "$HOME_ACTIVITY"; then
  fail "recents stayed open after scrolling (launcher came to the front)"
elif [ "$AFTER" -ne "$BEFORE" ]; then
  fail "no redirect while scrolling recents ($BEFORE -> $AFTER)"
else
  pass "recents stayed open after scrolling"
fi

echo "== 5. Leaving recents to home redirects"
adb shell input keyevent KEYCODE_HOME
if wait_top "$HOME_ACTIVITY" 8; then pass "recents -> home redirected"; else fail "recents -> home redirected"; fi
sleep 2

echo "== 5b. Recents opened from the launcher's own home screen"
# The everyday case. If recents were not recognised here, the redirect would close it at once.
BEFORE=$(redirect_count)
RECENTS_BEFORE=$(grep -c 'recents open' "$LOG" || true)
adb shell input keyevent KEYCODE_APP_SWITCH
sleep 6
AFTER=$(redirect_count)
RECENTS_AFTER=$(grep -c 'recents open' "$LOG" || true)
echo "      top:   $(top_activity)"
echo "      focus: $(focus)"
peek 02-recents-from-launcher
if [ "$AFTER" -ne "$BEFORE" ]; then
  fail "no redirect while recents is open ($BEFORE -> $AFTER)"
else
  pass "no redirect while recents is open"
fi
if [ "$RECENTS_AFTER" -gt "$RECENTS_BEFORE" ]; then
  pass "recents was recognised by view id"
else
  echo "NOTE: the system launcher was not the active window, so the view-id check was not exercised"
fi
# Android 13 shows the launcher itself as the live card here, so recents is confirmed by the
# thumbnail above (a scaled card with margins), not by a view id.

clear_anr
echo "== 5c. Leaving those recents to home redirects"
adb shell input keyevent KEYCODE_HOME
if wait_top "$HOME_ACTIVITY" 8; then pass "recents -> home redirected"; else fail "recents -> home redirected"; fi
sleep 1

clear_anr
echo "== 6. Screen off places the launcher in front"
adb shell am start -a android.settings.SETTINGS >/dev/null
wait_top "com.android.settings" 10 || true
sleep 1
adb shell input keyevent KEYCODE_SLEEP
sleep 4
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
sleep 2
if wait_top "$HOME_ACTIVITY" 10; then pass "launcher in front after screen off/on"; else
  fail "launcher in front after screen off/on"
  grep -E "screen off|REDIRECT \(screen off\)|launcher drawn" "$LOG" | tail -4 | sed 's/^/        /'
fi

echo "== 7. Settings screens open"
for screen in "" setup diagnostics log updates; do
  # --activity-clear-task recreates the activity so the new start screen is used.
  if [ -z "$screen" ]; then
    adb shell am start --activity-clear-task -n "$SETTINGS_ACTIVITY" >/dev/null
  else
    adb shell am start --activity-clear-task -n "$SETTINGS_ACTIVITY" --es screen "$screen" >/dev/null
  fi
  if wait_top "SettingsActivity" 8; then pass "settings screen '${screen:-main}'"; else fail "settings screen '${screen:-main}'"; fi
  sleep 3
  clear_anr
  adb exec-out screencap -p > "$OUT/04-settings-${screen:-main}.png" 2>/dev/null || true
  case "$screen" in
    "") texts 04-settings-main 12 ;;
    diagnostics) texts 04-settings-diagnostics 40 ;;
  esac
done

clear_anr
echo "== 7b. Glass benchmark runs through all three quality tiers"
adb shell am start --activity-clear-task -n "$SETTINGS_ACTIVITY" --es screen glass >/dev/null
wait_top "SettingsActivity" 8 || fail "glass benchmark opens"
sleep 6
peek 05-glass-running
sleep 12
texts 05-glass-finished 12
if top_activity | grep -q "SettingsActivity"; then pass "glass benchmark finished without leaving the screen"; else fail "glass benchmark finished without leaving the screen"; fi
# 0.9.12 (P4): once measured, the same screen tunes the real glass live, with its presets.
if python3 ci/find_text.py "$OUT/05-glass-finished.xml" "Tune the glass" >/dev/null && python3 ci/find_text.py "$OUT/05-glass-finished.xml" "Real glass" >/dev/null; then
  pass "after measuring, the glass can be tuned live with presets"
else
  fail "after measuring, the glass can be tuned live with presets"
fi

clear_anr
echo "== 9. Launcher UI: drawer, search, long-press menu, edit mode"
adb shell am start -n "$HOME_ACTIVITY" >/dev/null
wait_top "$HOME_ACTIVITY" 8 || true
sleep 2
# dump <name>: save the screen tree (quietly) so find_text can look things up.
dump() {
  adb shell rm -f /sdcard/ui.xml >/dev/null 2>&1
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/ui.xml > "$OUT/$1.xml" 2>/dev/null || true
}
on_screen() { python3 ci/find_text.py "$OUT/$1.xml" "$2" ${3:-} >/dev/null; }
tap_text() {
  local pos
  pos=$(python3 ci/find_text.py "$OUT/$1.xml" "$2" ${3:-}) || return 1
  adb shell input tap $pos
}

clear_anr
# Drawer: swipe up on the home screen (a second, slower try if the first does not open it).
show_drawer_log() { grep -E "Home: (swipe|drawer)" "$LOG" | tail -6 | sed 's/^/        /'; }
# 450 ms: on a slow emulator a faster swipe arrives as two or three points.
adb shell input swipe 540 1500 540 400 450
sleep 2
dump 06-drawer
if ! on_screen 06-drawer "Search apps"; then
  echo "      first swipe did not open the drawer; log so far:"
  show_drawer_log
  adb shell input swipe 540 1700 540 600 600
  sleep 2
  dump 06-drawer
fi
peek 06-drawer
echo "      bottom of the screen (search pill area):"
python3 -W ignore ci/ascii_shot.py "$OUT/06-drawer.png" 0.85
python3 ci/describe_ui.py "$OUT/06-drawer.xml" 8 bottom
if on_screen 06-drawer "Search apps"; then pass "swipe up opens the drawer"; else fail "swipe up opens the drawer"; fi
show_drawer_log
if on_screen 06-drawer "Settings" exact || on_screen 06-drawer "Chrome" exact; then pass "drawer lists apps"; else fail "drawer lists apps"; fi

# Long-press an app in the drawer: the glass menu, then add it to the home screen.
APP_LABEL=""
for candidate in Chrome Settings Clock Contacts; do
  if on_screen 06-drawer "$candidate" exact; then APP_LABEL=$candidate; break; fi
done
if [ -n "$APP_LABEL" ]; then
  POS=$(python3 ci/find_text.py "$OUT/06-drawer.xml" "$APP_LABEL" exact)
  adb shell input swipe $POS $POS 900
  sleep 1.5
  peek 07-menu
  dump 07-menu
  if on_screen 07-menu "App info" && on_screen 07-menu "Add to home"; then pass "long press opens the app menu"; else fail "long press opens the app menu"; fi
  if on_screen 07-menu "Tags" exact && on_screen 07-menu "Change icon" exact; then pass "menu offers tags and icon change"; else fail "menu offers tags and icon change"; fi
  tap_text 07-menu "Add to home" || true
  sleep 1
  adb shell input keyevent KEYCODE_BACK
  sleep 1.5
  dump 08-home-with-app
  if on_screen 08-home-with-app "$APP_LABEL" exact; then pass "Add to home places the app on the home screen"; else fail "Add to home places the app on the home screen"; fi
else
  fail "found an app to long-press in the drawer"
fi

# Back closes the drawer.
adb shell input keyevent KEYCODE_BACK
sleep 1
dump 09-after-back
if on_screen 09-after-back "Search apps"; then fail "back closes the drawer"; else pass "back closes the drawer"; fi
wait_top "$HOME_ACTIVITY" 3 || fail "launcher still in front after back"

# Search: swipe down, type a calculation.
clear_anr
adb shell input swipe 540 900 540 1700 250
sleep 1.5
adb shell input text "12+30"
sleep 1.5
peek 10-search
dump 10-search
if on_screen 10-search "= 42"; then pass "swipe down opens search and the calculator answers"; else fail "swipe down opens search and the calculator answers"; fi
# 0.7.1: with nothing typed, the search screen (the pill turned into its field at the bottom) offers widgets.
for i in 1 2 3 4 5; do adb shell input keyevent KEYCODE_DEL; done
sleep 1
peek 10b-search-empty
dump 10b-search-empty
if on_screen 10b-search-empty "Add widget" exact; then pass "the search screen offers widgets"; else fail "the search screen offers widgets"; fi
# 0.7.2: a question offers Ask Claude, and the field sits just above the keyboard (its height is in the log).
adb shell input text "how%sdo%smagnets%swork"
sleep 1.5
peek 10c-search-claude
dump 10c-search-claude
if on_screen 10c-search-claude "Ask Claude" exact; then pass "a question offers Ask Claude"; else fail "a question offers Ask Claude"; fi
KB=$(grep -o "keyboard height [0-9]* px" "$LOG" | tail -1 | grep -o "[0-9][0-9]*")
SCREEN_H=$(adb shell wm size | grep -o "[0-9]*x[0-9]*" | tail -1 | cut -dx -f2)
FIELD=$(python3 ci/find_text.py "$OUT/10c-search-claude.xml" "Search" exact | cut -d' ' -f2)
if [ -n "$KB" ] && [ -n "$FIELD" ] && [ -n "$SCREEN_H" ]; then
  KB_TOP=$((SCREEN_H - KB))
  echo "      keyboard top $KB_TOP px, search field centre $FIELD px"
  if [ "$FIELD" -lt "$KB_TOP" ] && [ "$FIELD" -gt $((KB_TOP - 260)) ]; then pass "the search field sits above the keyboard"; else fail "the search field sits above the keyboard"; fi
else
  echo "NOTE: no keyboard height in the log or no field found (keyboard '$KB', field '$FIELD'); field position not checked"
fi
adb shell input keyevent KEYCODE_BACK
sleep 0.5
adb shell input keyevent KEYCODE_BACK
sleep 1

clear_anr
# Home menu: long press on empty space shows the two glass pills.
adb shell input swipe 540 1250 540 1250 1000
sleep 1.5
peek 11-menu
dump 11-menu
if on_screen 11-menu "Wallpaper" exact && on_screen 11-menu "Layouts" exact; then pass "long press on empty space opens the home menu"; else fail "long press on empty space opens the home menu"; fi
if tap_text 11-menu "Wallpaper" exact; then
  sleep 1.5
  peek 11-strip
  dump 11-strip
  if on_screen 11-strip "Library and more" exact && on_screen 11-strip "Shuffle" exact; then pass "home menu opens the wallpaper strip"; else fail "home menu opens the wallpaper strip"; fi
  if tap_text 11-strip "Library and more" exact; then
    sleep 1.5
    peek 11-wallpaper
    dump 11-wallpaper
    if on_screen 11-wallpaper "Apply" exact && on_screen 11-wallpaper "Recent" exact; then pass "the strip opens the wallpaper screen"; else fail "the strip opens the wallpaper screen"; fi
    if tap_text 11-wallpaper "Blur and dim" exact; then
      sleep 1
      dump 11-look
      if on_screen 11-look "Drawer" exact; then pass "wallpaper screen has blur and dim per screen"; else fail "wallpaper screen has blur and dim per screen"; fi
      tap_text 11-look "Cancel" exact || adb shell input keyevent KEYCODE_BACK
    else
      fail "wallpaper screen has a Blur and dim page"
      tap_text 11-wallpaper "Cancel" exact || adb shell input keyevent KEYCODE_BACK
    fi
  else
    fail "wallpaper strip has a way to the wallpaper screen"
    adb shell input keyevent KEYCODE_BACK
  fi
  sleep 1
else
  fail "home menu has a Wallpaper item"
fi

clear_anr
# Edit mode from the home menu shows the edit toolbar.
adb shell input swipe 540 1250 540 1250 1000
sleep 1.5
dump 11-menu2
tap_text 11-menu2 "Edit" exact || true
sleep 1.5
peek 11-edit
dump 11-edit
if on_screen 11-edit "Done" exact; then pass "home menu opens edit mode"; else fail "home menu opens edit mode"; fi
if tap_text 11-edit "Pages" exact; then
  sleep 1
  dump 12-pages
  if on_screen 12-pages "Add page" exact && on_screen 12-pages "Home page" exact; then pass "edit mode shows the pages overview"; else fail "edit mode shows the pages overview"; fi
  tap_text 12-pages "Pages" exact || true
  sleep 0.5
  dump 11-edit
else
  fail "edit mode has a Pages button"
fi
# The widget list (clock, Now card, Today). Only opened and closed: adding a study widget asks for the calendar.
if tap_text 11-edit "Widgets" exact; then
  sleep 1
  dump 12-widgets
  if on_screen 12-widgets "Tap one to add it to this page" exact; then pass "edit mode lists the widgets"; else fail "edit mode lists the widgets"; fi
  adb shell input keyevent KEYCODE_BACK
  sleep 1
  dump 11-edit
else
  fail "edit mode has a Widgets button"
fi
tap_text 11-edit "Done" exact || adb shell input keyevent KEYCODE_BACK
sleep 1


clear_anr
# Study companion: the home menu's Today item opens the panel; on a fresh install it offers to switch on.
adb shell input swipe 540 1250 540 1250 1000
sleep 1.5
dump 14-menu
if tap_text 14-menu "Today" exact; then
  sleep 1.5
  peek 14-today
  dump 14-today
  if on_screen 14-today "Switch it on" exact; then pass "home menu opens the Today panel"; else fail "home menu opens the Today panel"; fi
  adb shell input keyevent KEYCODE_BACK
  sleep 1
else
  fail "home menu has a Today item"
  adb shell input keyevent KEYCODE_BACK
fi
clear_anr
# Study companion against the emulator's own calendar provider (0.6.3): a local calendar named "Study" with one block
# running now, written the way the assistant writes them. The line under the clock must show it, a tap must open Today,
# and a tick and "Done" must be written back into the event. Creating the calendar needs root (userdebug image);
# without it the checks are skipped, not failed. The device's "content" tool refuses a bound value that contains a
# colon, so the test block's title has no "Subject: topic" colon and the line shows the whole title.
echo "== 14b. Study block from a real calendar: clock line, Today, ticks written back"
NOW_S=$(adb shell date +%s | tr -d '\r')
cat > "$OUT/cal.sh" <<CALEOF
content insert --uri 'content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=smoke&account_type=LOCAL' \
  --bind account_name:s:smoke --bind account_type:s:LOCAL --bind name:s:Study --bind calendar_displayName:s:Study \
  --bind calendar_color:i:-14575885 --bind calendar_access_level:i:700 --bind ownerAccount:s:smoke --bind visible:i:1 --bind sync_events:i:1
CAL=\$(content query --uri content://com.android.calendar/calendars --projection _id:calendar_displayName | grep 'calendar_displayName=Study' | sed -E 's/.*_id=([0-9]+).*/\1/' | head -1)
D=\$(printf '[ ] Recall the theorems\n[ ] Serie 1, ex. 1-2\n\nWHAT smoke test block.\nP2-BLOCK 2026-11-15 smoke-1')
content insert --uri content://com.android.calendar/events --bind calendar_id:i:\$CAL --bind 'title:s:Proba 2 smoke block' \
  --bind description:s:"\$D" --bind dtstart:l:$(( (NOW_S - 1800) * 1000 )) --bind dtend:l:$(( (NOW_S + 3600) * 1000 )) --bind eventTimezone:s:UTC
T=\$(printf '[ ] Refaire le TD 3\n[ ] Corriger\n\nWHAT tomorrow smoke block, read only today.')
content insert --uri content://com.android.calendar/events --bind calendar_id:i:\$CAL --bind 'title:s:Micro smoke tomorrow' \
  --bind description:s:"\$T" --bind dtstart:l:$(( (NOW_S + 86400) * 1000 )) --bind dtend:l:$(( (NOW_S + 86400 + 3600) * 1000 )) --bind eventTimezone:s:UTC
content query --uri content://com.android.calendar/events --projection _id:title --where "calendar_id=\$CAL"
CALEOF
adb push "$OUT/cal.sh" /data/local/tmp/cal.sh >/dev/null 2>&1
adb shell su 0 sh /data/local/tmp/cal.sh > "$OUT/cal.txt" 2>&1 || adb shell sh /data/local/tmp/cal.sh > "$OUT/cal.txt" 2>&1
cat "$OUT/cal.txt" | tail -3
EVENT_ID=$(grep 'title=Proba 2 smoke block' "$OUT/cal.txt" | sed -E 's/.*_id=([0-9]+).*/\1/' | head -1)
event_text() { adb shell su 0 content query --uri content://com.android.calendar/events --projection title:description --where "_id=$EVENT_ID" 2>/dev/null | tr -d '\r'; }
if [ -z "$EVENT_ID" ]; then
  echo "SKIP: could not create a test calendar on this emulator (no root); study calendar checks not run"
else
  adb shell pm grant "$PKG" android.permission.READ_CALENDAR 2>/dev/null
  adb shell pm grant "$PKG" android.permission.WRITE_CALENDAR 2>/dev/null
  adb shell input swipe 540 1250 540 1250 1000
  sleep 1.5
  dump 14b-menu
  tap_text 14b-menu "Today" exact || true
  sleep 1.5
  dump 14b-today
  tap_text 14b-today "Switch it on" exact || true
  sleep 3
  adb shell input keyevent KEYCODE_BACK
  sleep 2
  peek 14b-home
  dump 14b-home
  if on_screen 14b-home "Proba 2 smoke block · " && on_screen 14b-home "min left"; then pass "the clock line shows the running block"; else fail "the clock line shows the running block"; fi
  if tap_text 14b-home "min left"; then
    sleep 1.5
    peek 14b-panel
    dump 14b-panel
    if on_screen 14b-panel "Recall the theorems" exact; then pass "the clock line opens Today with the block's tasks"; else fail "the clock line opens Today with the block's tasks"; fi
    # "Done" first: once a task is ticked, its tick icon is also labelled "Done" (for accessibility) and comes earlier on screen.
    tap_text 14b-panel "Done" exact || true
    sleep 3
    if event_text | grep -q 'title=✓ Proba 2 smoke block'; then pass "Done puts ✓ in front of the event's title"; else fail "Done puts ✓ in front of the event's title"; event_text | head -8; fi
    if event_text | grep -q '— launcher: done'; then pass "the launcher's status line is written"; else fail "the launcher's status line is written"; fi
    dump 14b-panel2
    tap_text 14b-panel2 "Recall the theorems" exact || true
    sleep 3
    if event_text | grep -q '\[x\] Recall the theorems'; then pass "a tick is written into the event's description"; else fail "a tick is written into the event's description"; event_text | head -8; fi
    if event_text | grep -q '— launcher: done .* · 1/2 tasks'; then pass "the status line counts the ticked task"; else fail "the status line counts the ticked task"; event_text | tail -2; fi
    # 0.6.4: tomorrow opens from Today, and each of its blocks opens to its steps.
    dump 14b-panel3
    if tap_text 14b-panel3 "Tomorrow · "; then
      sleep 1.5
      dump 14b-tomorrow
      if tap_text 14b-tomorrow "Micro smoke tomorrow" exact; then
        sleep 1.5
        peek 14b-tomorrow-open
        dump 14b-tomorrow-open
        if on_screen 14b-tomorrow-open "Refaire le TD 3" exact; then pass "a block of tomorrow opens to its steps"; else fail "a block of tomorrow opens to its steps"; fi
      else
        fail "Today lists tomorrow's block once tomorrow is opened"
      fi
    else
      fail "Today shows a Tomorrow section"
    fi
    adb shell input keyevent KEYCODE_BACK
    sleep 2
    # With the block done and no task left, the clock line moves on to tomorrow instead of vanishing.
    dump 14b-home2
    peek 14b-home2
    if on_screen 14b-home2 "· Micro smoke tomorrow"; then pass "with today done the clock line shows tomorrow's first block"; else fail "with today done the clock line shows tomorrow's first block"; fi
  else
    fail "the clock line can be tapped"
  fi
fi

clear_anr
# 0.9.21 (C5, entry 22): "Quiet during blocks" holds a notification while a block runs. A second block running now
# goes into the test calendar, notification access is given with adb, the setting is switched on in Settings →
# Study companion, and a test notification is posted from the shell. Set-up problems print a NOTE (the emulator's
# fault, not the launcher's); a notification that was not held fails.
echo "== 14d. Quiet during blocks holds a notification"
if [ -n "$EVENT_ID" ]; then
  cat > "$OUT/cal2.sh" <<CAL2EOF
CAL=\$(content query --uri content://com.android.calendar/calendars --projection _id:calendar_displayName | grep 'calendar_displayName=Study' | sed -E 's/.*_id=([0-9]+).*/\1/' | head -1)
content insert --uri content://com.android.calendar/events --bind calendar_id:i:\$CAL --bind 'title:s:Stat Des quiet smoke' \
  --bind dtstart:l:$(( ($(adb shell date +%s | tr -d '\r') - 600) * 1000 )) --bind dtend:l:$(( ($(adb shell date +%s | tr -d '\r') + 3000) * 1000 )) --bind eventTimezone:s:UTC
CAL2EOF
  adb push "$OUT/cal2.sh" /data/local/tmp/cal2.sh >/dev/null 2>&1
  adb shell su 0 sh /data/local/tmp/cal2.sh >/dev/null 2>&1 || adb shell sh /data/local/tmp/cal2.sh >/dev/null 2>&1
  adb shell cmd notification allow_listener "$PKG/dev.liquid.launcher.QuietService" >/dev/null 2>&1
  adb shell am start --activity-clear-task -n "$SETTINGS_ACTIVITY" --es screen section:study >/dev/null
  wait_top "SettingsActivity" 8 || true
  sleep 2
  QUIET_ON=0
  for i in 1 2 3 4 5 6 7 8 9 10; do
    dump 14d-settings
    if tap_text 14d-settings "Quiet during blocks" exact; then QUIET_ON=1; break; fi
    adb shell input swipe 540 1700 540 700 400
    sleep 1
  done
  sleep 1
  adb shell am start -n "$HOME_ACTIVITY" >/dev/null
  wait_top "$HOME_ACTIVITY" 8 || true
  sleep 5
  if [ "$QUIET_ON" = 1 ] && grep -q "Quiet: notification access on" "$LOG"; then
    adb shell cmd notification post -t "Quiet smoke" quietsmoke "This should wait until the block ends" >/dev/null 2>&1
    sleep 3
    if grep -q "Quiet: held a notification" "$LOG"; then
      pass "a notification posted during a block waits until it ends"
    else
      fail "a notification posted during a block waits until it ends"
      grep -E "Quiet:|Study:" "$LOG" | tail -8 | sed 's/^/        /'
    fi
  else
    echo "NOTE: quiet during blocks not checked (setting found: $QUIET_ON; access: $(grep -c 'Quiet: notification access on' "$LOG"))"
  fi
else
  echo "NOTE: quiet during blocks not checked (no test calendar)"
fi

clear_anr
# 0.6.4: the letter index bends out from under the finger and the bubble sits past the letters. A picture only:
# the screenshot is taken while a slow swipe holds the right edge of the drawer.
echo "== 14c. Letter index while held"
adb shell input keyevent KEYCODE_HOME
sleep 2.5
adb shell input swipe 540 1500 540 400 450
sleep 2
dump 14c-drawer
if ! on_screen 14c-drawer "Search apps"; then
  adb shell input swipe 540 1700 540 600 600
  sleep 2
  dump 14c-drawer
fi
on_screen 14c-drawer "Search apps" || echo "NOTE: the drawer did not open; the picture below shows the home screen"
# 0.9.6: the index is a short rail low on the right (Rail position 26 % from the bottom); drag along it slowly.
adb shell input swipe 1060 1720 1060 1360 3000 &
SWIPE_PID=$!
sleep 1.6
peek 14c-index
wait "$SWIPE_PID" 2>/dev/null
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 1

clear_anr
# 0.7.0: another app's widget (last, so the widget it places cannot be under a later check's long press). The picker lists the apps that offer widgets; searching "clock" opens the Clock app's
# widgets; the system asks once whether the launcher may add widgets ("Create"); the clock widget then runs on the home
# screen as the app's own views (a TextClock or an AnalogClock in the screen tree).
echo "== 12b. Another app's widget"
# The launcher must be in front: on a slow emulator a redirect can be lost while the accessibility service reconnects
# (run 57: the long press below reached the system launcher and opened its own widget list).
if ! wait_top "$HOME_ACTIVITY" 2; then
  echo "NOTE: the launcher was not in front before 12b; starting it"
  adb shell am start -n "$HOME_ACTIVITY" >/dev/null
  wait_top "$HOME_ACTIVITY" 6 || echo "NOTE: the launcher is still not in front"
  sleep 1.5
fi
# 14c ends with Home over the open drawer. On a slow emulator the trip through the system launcher can take longer
# than the launcher's 1.5 s "Home press" window (run 59: an 829 ms window check), so the drawer stays open and the
# long press below would land on an app. Close it first.
dump 12b-before
if on_screen 12b-before "Search apps"; then
  echo "NOTE: the drawer was still open before 12b (a slow Home press); closing it"
  adb shell input keyevent KEYCODE_BACK
  sleep 1.5
fi
adb shell input swipe 540 1250 540 1250 1000
sleep 1.5
dump 12b-menu
if tap_text 12b-menu "Widgets" exact; then
  sleep 2
  dump 12b-picker
  peek 12b-picker
  if tap_text 12b-picker "Search widgets and apps" exact; then
    sleep 0.5
    adb shell input text clock
    sleep 2
    dump 12b-search
    peek 12b-search
    WIDGET=""
    # The Clock app calls them "Digital" and "Analog" on the emulator's image (run 51), "… clock" on others.
    for name in "Digital clock" "Analog clock" "Digital" "Analog"; do
      if on_screen 12b-search "$name" exact; then WIDGET="$name"; break; fi
    done
    if [ -n "$WIDGET" ]; then
      tap_text 12b-search "$WIDGET" exact
      sleep 3
      dump 12b-bind
      peek 12b-bind
      tap_text 12b-bind "Create" exact || tap_text 12b-bind "Allow" exact || true
      sleep 3
      # A widget with a set-up screen opens it now (the Clock app's Analog asks for a clock face): pick the first choice.
      peek 12b-after
      echo "      top: $(top_activity)"
      if ! top_activity | grep -q "$PKG"; then
        dump 12b-setup
        for choice in Scallop Numeral Done Save OK; do
          if tap_text 12b-setup "$choice" exact; then echo "      set-up screen: picked \"$choice\""; break; fi
        done
        sleep 3
      fi
      dump 12b-home
      peek 12b-home
      # The launcher logs each step; "added" means the widget's item is on the page with the app's views in it.
      if grep -q "Widgets: added \"" "$LOG"; then
        pass "another app's widget ($WIDGET) is placed on the home screen"
      else
        fail "another app's widget ($WIDGET) is placed on the home screen"
        grep -o 'class="[^"]*"' "$OUT/12b-home.xml" | sort | uniq -c | sort -rn | head -12
      fi
      grep "Widgets:" "$LOG" | tail -6 | sed 's/^/        /'
    else
      echo "SKIP: no Clock app widget on this emulator; another app's widget not placed"
    fi
  else
    fail "the widget picker has a search field"
  fi
else
  fail "the home menu has a Widgets item"
fi
adb shell input keyevent KEYCODE_HOME
sleep 1

clear_anr
# The updates screen (in-app updater) opens and offers a check; no network is used here.
adb shell am start --activity-clear-task -n "$SETTINGS_ACTIVITY" --es screen updates >/dev/null
sleep 3
dump 13-updates
if on_screen 13-updates "Check for updates" exact; then pass "updates screen shows the check button"; else fail "updates screen shows the check button"; fi
adb shell input keyevent KEYCODE_BACK
sleep 1

# Wait for the service to be back after the screen dumps before the crash check.
for i in $(seq 1 40); do service_up && break; sleep 0.5; done

if [ "$ANR_DIALOGS" -gt 0 ]; then echo "NOTE: dismissed $ANR_DIALOGS 'not responding' dialog(s); the emulator was slow (see logcat for ANR reasons)"; fi
echo "== 15. Wallpaper transition shaders"
# 0.7.0: ripple, ink, shine and melt are AGSL shaders compiled when the home screen starts; a shader that does not
# compile falls back to the liquid fade, so it would never show as a crash. The launcher logs which compiled.
if grep -q "transition shaders ready: ripple, ink, shine, melt" "$LOG" && ! grep -q "transition shader.*unavailable" "$LOG"; then
  pass "the four transition shaders compile"
else
  fail "the four transition shaders compile"
  grep -E "transition shader" "$LOG" | tail -6
fi

echo "== 16. Real glass shader"
# 0.9.12 (P4): the glass's AGSL shader compiles when the home screen starts; if it did not, the glass would quietly
# fall back to the simple lens, so the launcher logs which it got.
if grep -q "liquid glass shader ready" "$LOG"; then
  pass "the real glass shader compiles"
else
  fail "the real glass shader compiles"
  grep -E "liquid glass shader" "$LOG" | tail -4
fi

echo "== 8. No crashes"
adb logcat -d > "$OUT/logcat.txt" 2>/dev/null
if grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG"; then
  fail "no crash in logcat"
  grep -A25 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80
else
  pass "no crash in logcat"
fi

kill "$LOGCAT_PID" 2>/dev/null
echo "== Launcher log"
sed -E 's/^([0-9-]+ )?([0-9:.]+) .*LiquidLauncher[^:]*: /\2 /' "$LOG" | tail -220

echo
if [ "$FAILS" -eq 0 ]; then
  echo "SMOKE RESULT: all checks passed"
else
  echo "SMOKE RESULT: $FAILS check(s) failed"
fi
exit "$FAILS"
