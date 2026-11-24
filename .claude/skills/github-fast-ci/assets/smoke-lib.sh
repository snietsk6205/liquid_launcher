#!/usr/bin/env bash
# Helpers for an Android emulator test written as a shell script (from Liquid Launcher's ci/smoke.sh).
# Source it at the top of ci/smoke.sh:
#
#   set -u
#   PKG=com.you.app
#   OUT=out/smoke; LOG="$OUT/app.log"; mkdir -p "$OUT"
#   . ci/smoke-lib.sh
#   start_app_log "YourLogTag"             # streams the app's own log lines into $LOG
#   section "1. The main screen opens"
#   adb shell am start -n "$PKG/.MainActivity"
#   wait_screen 01-main "Welcome" 10 || true
#   if on_screen 01-main "Welcome"; then pass "the main screen opens"; else fail "the main screen opens"; look 01-main; fi
#   ...
#   finish_smoke "all"                     # prints the summary and exits with the number of failed checks
#
# Principles: never sleep a fixed time for something to happen (wait for it, with a timeout); every wait that took
# 5 s or more, or gave up, is listed at the end; every section's time is listed at the end; a failed check prints what
# was on screen; the script runs every check even after a failure (one run shows every problem).
# Needs: adb on PATH, python3, ci/find_text.py, ci/describe_ui.py, ci/ascii_shot.py (+ Pillow for the ASCII shots).

FAILS=0
FAILED_CHECKS=""
WAIT_NOTES=""
SECTION_NAME=""
SECTION_START=$SECONDS
SECTION_TIMES=""

# section <name> : starts a timed section ("-" closes the last one without starting another).
section() {
  if [ -n "$SECTION_NAME" ]; then
    SECTION_TIMES="$SECTION_TIMES
$(printf '%5d s  %s' $((SECONDS - SECTION_START)) "$SECTION_NAME")"
  fi
  SECTION_NAME=$1
  SECTION_START=$SECONDS
  [ "$1" = "-" ] || echo "== $1"
}

pass() { echo "PASS: $1"; }
note() { echo "NOTE: $1"; }
fail() {
  echo "FAIL: $1"
  echo "      top: $(top_activity)"
  FAILS=$((FAILS + 1))
  FAILED_CHECKS="$FAILED_CHECKS
  - $1"
}

top_activity() { adb shell dumpsys activity activities 2>/dev/null | grep -E 'topResumedActivity|mResumedActivity' | head -1; }

# note_wait <started at> <status> <what> : records a slow or failed wait; returns <status>.
note_wait() {
  local took=$((SECONDS - $1)) how=""
  [ "$2" -ne 0 ] && how=", gave up"
  if [ "$2" -ne 0 ] || [ "$took" -ge 5 ]; then
    WAIT_NOTES="$WAIT_NOTES
$(printf '%5d s  %s%s  (%s)' "$took" "$3" "$how" "${SECTION_NAME:-start}")"
  fi
  return "$2"
}

# wait_until <seconds> <command…> : succeeds as soon as the command does (checked every 0.25 s).
wait_until() {
  local seconds=$1 i start=$SECONDS
  shift
  for i in $(seq 1 $((seconds * 4))); do
    if "$@" >/dev/null 2>&1; then note_wait "$start" 0 "until: $*"; return; fi
    sleep 0.25
  done
  note_wait "$start" 1 "until: $*"
}

# wait_top <pattern> <seconds> : succeeds once the resumed activity matches.
wait_top() {
  local pattern=$1 seconds=$2 i start=$SECONDS
  for i in $(seq 1 $((seconds * 4))); do
    if top_activity | grep -q "$pattern"; then note_wait "$start" 0 "top: $pattern"; return; fi
    sleep 0.25
  done
  note_wait "$start" 1 "top: $pattern"
}

# wait_log <pattern> <seconds> : succeeds once the app's log ($LOG) contains the pattern.
wait_log() {
  local pattern=$1 seconds=$2 i start=$SECONDS
  for i in $(seq 1 $((seconds * 2))); do
    if grep -q "$pattern" "$LOG"; then note_wait "$start" 0 "log: $pattern"; return; fi
    sleep 0.5
  done
  note_wait "$start" 1 "log: $pattern"
}

# start_app_log <tag> : streams the app's log lines (logcat tag) into $LOG, as a stream so nothing is lost to rotation.
start_app_log() {
  adb logcat -c
  adb logcat -v time -s "$1:I" > "$LOG" 2>/dev/null &
  LOGCAT_PID=$!
}

# Screens: dump the UI tree, find text in it, tap it, print it as ASCII and as a list of texts.
dump() {
  adb shell rm -f /sdcard/ui.xml >/dev/null 2>&1
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/ui.xml > "$OUT/$1.xml" 2>/dev/null || true
}
dump_ok() { grep -q '<hierarchy' "$OUT/$1.xml" 2>/dev/null; }
on_screen() { python3 ci/find_text.py "$OUT/$1.xml" "$2" ${3:-} >/dev/null; }
tap_text() {
  local pos
  pos=$(python3 ci/find_text.py "$OUT/$1.xml" "$2" ${3:-}) || return 1
  adb shell input tap $pos
}
peek() {
  adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || true
  echo "-- screen: $1"
  python3 -W ignore ci/ascii_shot.py "$OUT/$1.png"
}
texts() {
  dump "$1"
  python3 ci/describe_ui.py "$OUT/$1.xml" "${2:-40}"
}
look() { peek "$1"; texts "$1" "${2:-40}"; }

# wait_screen <name> <text> <seconds> [exact] : dumps the screen until the text is on it (the last dump stays as <name>).
# A screen still moving cannot be dumped ("could not get idle state"): it is tried again.
wait_screen() {
  local name=$1 text=$2 end=$((SECONDS + $3)) mode=${4:-} start=$SECONDS
  while :; do
    dump "$name"
    if on_screen "$name" "$text" $mode; then note_wait "$start" 0 "screen: $text"; return; fi
    if [ "$SECONDS" -ge "$end" ]; then note_wait "$start" 1 "screen: $text"; return; fi
    sleep 0.4
  done
}

# wait_gone <name> <text> <seconds> [exact] : until the text is no longer on screen.
wait_gone() {
  local name=$1 text=$2 end=$((SECONDS + $3)) mode=${4:-} start=$SECONDS
  while :; do
    dump "$name"
    if dump_ok "$name" && ! on_screen "$name" "$text" $mode; then note_wait "$start" 0 "gone: $text"; return; fi
    if [ "$SECONDS" -ge "$end" ]; then note_wait "$start" 1 "gone: $text"; return; fi
    sleep 0.4
  done
}

# idle_frames <label> [limit] : frames the app draws in 3 s while the screen sits still (a still screen draws ~0;
# one that keeps drawing has an animation that never ends).
gfx_frames() { adb shell dumpsys gfxinfo "$PKG" 2>/dev/null | tr -d '\r' | grep -m1 'Total frames rendered' | grep -oE '[0-9]+' | head -1; }
idle_frames() {
  local label=$1 limit=${2:-20} a b n
  a=$(gfx_frames); sleep 3; b=$(gfx_frames)
  if [ -z "$a" ] || [ -z "$b" ]; then note "idle frames of $label not readable"; return 0; fi
  n=$((b - a))
  if [ "$n" -le "$limit" ]; then pass "$label is still when idle ($n frames in 3 s)"; else fail "$label is still when idle ($n frames in 3 s, limit $limit)"; fi
}

# no_crash : fails when logcat shows a fatal exception in the app.
no_crash() {
  adb logcat -d > "$OUT/logcat.txt" 2>/dev/null
  if grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG"; then
    fail "no crash in logcat"
    grep -A25 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80
  else
    pass "no crash in logcat"
  fi
}

# finish_smoke <part> : the summary every run ends with, then exits with the number of failed checks.
finish_smoke() {
  no_crash
  [ -n "${LOGCAT_PID:-}" ] && kill "$LOGCAT_PID" 2>/dev/null
  section "App log (last lines)"
  tail -200 "$LOG" 2>/dev/null
  section "-"
  echo
  echo "TIME PER SECTION, part '$1' ($SECONDS s in all):$SECTION_TIMES"
  echo
  echo "SLOW WAITS (5 s or more, or gave up):${WAIT_NOTES:-
  none}"
  echo
  if [ "$FAILS" -eq 0 ]; then
    echo "SMOKE RESULT ($1): all checks passed"
  else
    echo "SMOKE RESULT ($1): $FAILS check(s) failed:$FAILED_CHECKS"
  fi
  exit "$FAILS"
}
