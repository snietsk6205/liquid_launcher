#!/usr/bin/env bash
# Waits, up to a time limit, until a freshly booted emulator has finished its first-boot work (0.11.2). For a minute or
# two after Android says "booted" it still hands BOOT_COMPLETED to every app and Play services start up; while that runs,
# the launcher can be starved into "not responding" (run 101: killed twice in its first minute, so its redirect service
# was away for checks 2 and 3). Used before the emulator's snapshot is saved, so every run starts from a calm phone, and
# at the start of smoke.sh, for a phone that is not calm yet (a cold boot). Never fails: past the limit it goes on.
#
# Calm: the CPU busy under 40 % (from /proc/stat, over about 2 s) three readings in a row. After a snapshot is restored
# the emulator loads its memory as it is touched, so the first minute or two is slow even with nothing left to boot
# (run 102: 92 % busy, 42 % of it in the kernel, before its first "not responding").
set -u
limit=${1:-180}
end=$((SECONDS + limit))
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  [ "$SECONDS" -ge "$end" ] && { echo "settle: not booted after $limit s; going on"; exit 0; }
  sleep 2
done
cpu() { adb shell head -1 /proc/stat 2>/dev/null | tr -d '\r'; }
# The share of CPU time spent busy between two readings of /proc/stat's "cpu" line, in %; empty when unreadable.
busy() {
  local a b
  a=$(cpu); sleep 2; b=$(cpu)
  printf '%s\n%s\n' "$a" "$b" | awk '$1 == "cpu" { idle = $5 + $6; total = 0; for (i = 2; i <= 9 && i <= NF; i++) total += $i
    if (n++ == 0) { i0 = idle; t0 = total } else { i1 = idle; t1 = total } }
    END { if (n == 2 && t1 > t0) printf "%d", 100 * (1 - (i1 - i0) / (t1 - t0)) }'
}
calm=0
seen=""
while [ "$SECONDS" -lt "$end" ]; do
  b=$(busy)
  if [ -z "$b" ]; then echo "settle: /proc/stat cannot be read here; waiting 30 s"; sleep 30; exit 0; fi
  seen="$seen $b"
  if [ "$b" -lt 40 ]; then calm=$((calm + 1)); else calm=0; fi
  if [ "$calm" -ge 3 ]; then echo "settle: calm after $SECONDS s (CPU busy, %:$seen)"; exit 0; fi
done
echo "settle: still busy after $limit s (CPU busy, %:$seen); going on"
exit 0
