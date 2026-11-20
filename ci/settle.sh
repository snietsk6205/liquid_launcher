#!/usr/bin/env bash
# Waits, up to a time limit, until a freshly booted emulator has finished its first-boot work (0.11.2). For a minute or
# two after Android says "booted" it still hands BOOT_COMPLETED to every app and Play services start up; while that runs,
# the launcher can be starved into "not responding" (run 101: killed twice in its first minute, so its redirect service
# was away for checks 2 and 3). Used before the emulator's snapshot is saved, so every run starts from a calm phone, and
# at the start of smoke.sh, for a phone that is not calm yet (a cold boot). Never fails: past the limit it goes on.
#
# Calm: the CPU pressure ("some avg10" of /proc/pressure/cpu, the share of time something waited for a CPU over the
# last 10 s) under 20 % four readings in a row, 3 s apart. Where that file cannot be read, a fixed 30 s instead.
set -u
limit=${1:-180}
end=$((SECONDS + limit))
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  [ "$SECONDS" -ge "$end" ] && { echo "settle: not booted after $limit s; going on"; exit 0; }
  sleep 2
done
pressure() { adb shell cat /proc/pressure/cpu 2>/dev/null | tr -d '\r' | sed -n 's/^some avg10=\([0-9]*\).*/\1/p'; }
p=$(pressure)
if [ -z "$p" ]; then
  echo "settle: the CPU pressure cannot be read here; waiting 30 s"
  sleep 30
  exit 0
fi
echo "settle: CPU pressure $p % at the start"
calm=0
while [ "$SECONDS" -lt "$end" ]; do
  p=$(pressure)
  if [ -n "$p" ] && [ "$p" -lt 20 ]; then calm=$((calm + 1)); else calm=0; fi
  if [ "$calm" -ge 4 ]; then echo "settle: calm after $SECONDS s (CPU pressure $p %)"; exit 0; fi
  sleep 3
done
echo "settle: still busy after $limit s (CPU pressure ${p:-unknown} %); going on"
exit 0
