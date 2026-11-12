#!/usr/bin/env bash
# End-to-end check on an emulator: installs the release APK, enables the accessibility
# service and verifies the redirect against the AOSP launcher (same QuickStep base as
# HONOR Home): home redirects, recents is left alone, leaving recents redirects.
set -u

PKG=dev.liquid.launcher
HOME_ACTIVITY="$PKG/.MainActivity"
SETTINGS_ACTIVITY="$PKG/dev.liquid.launcher.feature.settings.SettingsActivity"
SERVICE="$PKG/dev.liquid.launcher.feature.redirect.LauncherAccessibilityService"
OUT=out/smoke
FAILS=0
mkdir -p "$OUT"

APK=$(ls out/*.apk | head -1)
echo "== Installing $APK"
adb install -r "$APK" || { echo "FAIL: install"; exit 1; }

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

pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; echo "      top: $(top_activity)"; FAILS=$((FAILS + 1)); }
shot() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || true; }
redirect_count() { adb logcat -d -s LiquidLauncher:I 2>/dev/null | grep -c 'REDIRECT (' || true; }

echo "== Default launcher"
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
adb shell wm dismiss-keyguard || true
adb shell input keyevent KEYCODE_HOME
sleep 2

echo "== Enabling the accessibility service"
adb logcat -c
adb shell settings put secure enabled_accessibility_services "$SERVICE"
adb shell settings put secure accessibility_enabled 1
sleep 3

echo "== 1. App starts"
adb shell am start -n "$HOME_ACTIVITY" >/dev/null
if wait_top "$HOME_ACTIVITY" 15; then pass "launcher activity starts"; else fail "launcher activity starts"; fi
sleep 3
shot 01-home
if adb logcat -d -s LiquidLauncher:I | grep -q 'service connected'; then
  pass "accessibility service connected"
else
  fail "accessibility service connected"
fi

echo "== 2. Home button while on the launcher returns to the launcher"
BEFORE=$(redirect_count)
adb shell input keyevent KEYCODE_HOME
sleep 3
AFTER=$(redirect_count)
if wait_top "$HOME_ACTIVITY" 5 && [ "$AFTER" -gt "$BEFORE" ]; then
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

echo "== 4. Recents is not interrupted"
adb shell am start -a android.settings.SETTINGS >/dev/null
wait_top "com.android.settings" 10 || true
sleep 2
BEFORE=$(redirect_count)
adb shell input keyevent KEYCODE_APP_SWITCH
sleep 5
shot 02-recents
AFTER=$(redirect_count)
if top_activity | grep -q "$HOME_ACTIVITY"; then
  fail "recents stayed open (launcher came to the front)"
elif [ "$AFTER" -ne "$BEFORE" ]; then
  fail "no redirect while recents is open ($BEFORE -> $AFTER)"
else
  pass "recents stayed open"
fi
if adb logcat -d -s LiquidLauncher:I | grep -q 'recents open'; then
  pass "recents was recognised"
else
  fail "recents was recognised"
fi

echo "== 5. Leaving recents to home redirects"
adb shell input keyevent KEYCODE_HOME
if wait_top "$HOME_ACTIVITY" 6; then pass "recents -> home redirected"; else fail "recents -> home redirected"; fi
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
shot 03-after-unlock

echo "== 7. Settings screens open"
for screen in "" setup diagnostics log; do
  # --activity-clear-task recreates the activity so the new start screen is used.
  if [ -z "$screen" ]; then
    adb shell am start --activity-clear-task -n "$SETTINGS_ACTIVITY" >/dev/null
  else
    adb shell am start --activity-clear-task -n "$SETTINGS_ACTIVITY" --es screen "$screen" >/dev/null
  fi
  if wait_top "SettingsActivity" 8; then pass "settings screen '${screen:-main}'"; else fail "settings screen '${screen:-main}'"; fi
  sleep 2
  shot "04-settings-${screen:-main}"
done

echo "== 8. No crashes"
adb logcat -d > "$OUT/logcat.txt" 2>/dev/null
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" && grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG"; then
  fail "no crash in logcat"
  grep -A25 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80
else
  pass "no crash in logcat"
fi

echo "== Launcher log"
grep -E 'LiquidLauncher' "$OUT/logcat.txt" | sed -E 's/^.*LiquidLauncher: //' | tail -150

echo
if [ "$FAILS" -eq 0 ]; then
  echo "SMOKE RESULT: all checks passed"
else
  echo "SMOKE RESULT: $FAILS check(s) failed"
fi
exit "$FAILS"
