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
adb install -r "$APK" || { echo "FAIL: install"; exit 1; }

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

pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; echo "      top: $(top_activity)"; FAILS=$((FAILS + 1)); }
redirect_count() { grep -c 'REDIRECT (' "$LOG" || true; }

# look <name> : screenshot, rough character thumbnail and the texts on screen.
look() {
  adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || true
  echo "-- screen: $1"
  python3 ci/ascii_shot.py "$OUT/$1.png"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/ui.xml > "$OUT/$1.xml" 2>/dev/null || true
  python3 ci/describe_ui.py "$OUT/$1.xml" "${2:-40}"
}

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
fi
sleep 2

echo "== 1. App starts"
adb shell am start -n "$HOME_ACTIVITY" >/dev/null
if wait_top "$HOME_ACTIVITY" 15; then pass "launcher activity starts"; else fail "launcher activity starts"; fi
sleep 4
look 01-home 30

echo "== 2. Home button while on the launcher returns to the launcher"
BEFORE=$(redirect_count)
adb shell input keyevent KEYCODE_HOME
sleep 4
AFTER=$(redirect_count)
if wait_top "$HOME_ACTIVITY" 6 && [ "$AFTER" -gt "$BEFORE" ]; then
  pass "home press redirected ($BEFORE -> $AFTER)"
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

echo "== 4b. Recents shown by the system launcher itself is recognised"
# Swipe the front card away: another card remains, and the system launcher is now the
# active window while recents is still open. This is the case the view-id check exists for.
adb shell input swipe 540 1300 540 150 200
sleep 5
look 02-recents 12
AFTER=$(redirect_count)
echo "      top: $(top_activity)"
if top_activity | grep -q "$HOME_ACTIVITY"; then
  fail "recents stayed open after dismissing a card (launcher came to the front)"
elif [ "$AFTER" -ne "$BEFORE" ]; then
  fail "no redirect while recents is open ($BEFORE -> $AFTER)"
else
  pass "recents stayed open after dismissing a card"
fi
if grep -q 'recents open' "$LOG"; then
  pass "recents was recognised by view id"
else
  echo "NOTE: the system launcher never became the active window during recents, so the view-id check was not exercised"
fi

echo "== 5. Leaving recents to home redirects"
adb shell input keyevent KEYCODE_HOME
if wait_top "$HOME_ACTIVITY" 8; then pass "recents -> home redirected"; else fail "recents -> home redirected"; fi
sleep 1

echo "== 6. Screen off places the launcher in front"
adb shell am start -a android.settings.SETTINGS >/dev/null
wait_top "com.android.settings" 10 || true
sleep 1
adb shell input keyevent KEYCODE_SLEEP
sleep 4
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
sleep 2
if wait_top "$HOME_ACTIVITY" 6; then pass "launcher in front after screen off/on"; else fail "launcher in front after screen off/on"; fi

echo "== 7. Settings screens open"
for screen in "" setup diagnostics log; do
  # --activity-clear-task recreates the activity so the new start screen is used.
  if [ -z "$screen" ]; then
    adb shell am start --activity-clear-task -n "$SETTINGS_ACTIVITY" >/dev/null
  else
    adb shell am start --activity-clear-task -n "$SETTINGS_ACTIVITY" --es screen "$screen" >/dev/null
  fi
  if wait_top "SettingsActivity" 8; then pass "settings screen '${screen:-main}'"; else fail "settings screen '${screen:-main}'"; fi
  sleep 3
  case "$screen" in
    "") look 04-settings-main 30 ;;
    diagnostics) look 04-settings-diagnostics 60 ;;
    *) adb exec-out screencap -p > "$OUT/04-settings-$screen.png" 2>/dev/null || true ;;
  esac
done

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
