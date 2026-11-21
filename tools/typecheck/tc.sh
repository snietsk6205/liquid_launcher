#!/bin/bash
# Local type check of the whole launcher against desktop Compose + android-all + stubs.
# Usage: tc.sh baseline   (records today's errors, which are API-gap noise)
#        tc.sh            (prints only errors that are not in the baseline)
cd "$(dirname "$0")"
REPO=$(cd "$(dirname "$0")/../.." && pwd)
CP=$(ls lib/*.jar | tr '\n' ':')
find $REPO -name '*.kt' \( -path '*/src/main/kotlin/*' -o -path '*/src/test/kotlin/*' \) -not -path '*/build/*' > sources.txt
rm -rf out; mkdir -p out
start=$(date +%s)
java -Xmx6g -Xss8m -cp "kc/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 21 -nowarn \
  -cp "$CP" \
  -Xplugin=kc/kotlin-compose-compiler-plugin-embeddable-2.4.10.jar \
  -Xplugin=kc/kotlin-serialization-compiler-plugin-embeddable-2.4.10.jar \
  -d out stubs/*.kt $(cat sources.txt) > raw.txt 2>&1
echo "compiled in $(( $(date +%s) - start )) s"
# "e: file:///x/Y.kt:12:5 message" -> "x/Y.kt: message" (line numbers dropped so edits elsewhere do not show up)
REPO="$REPO" python3 - "$1" <<'PY'
import re, sys, collections, os
REPO = os.environ["REPO"]
mode = sys.argv[1] if len(sys.argv) > 1 else ""
errs = []
full = []
for line in open("raw.txt", encoding="utf-8", errors="replace"):
    m = re.match(r"^(?:e: )?(?:file://)?(/[^:]+\.kt):(\d+):(\d+):? (?:error: )?(.*)$", line.rstrip("\n"))
    if m and " warning: " in line: m = None
    if m:
        path = m.group(1).replace(REPO + "/", "")
        errs.append(f"{path}: {m.group(4)}")
        full.append(f"{path}:{m.group(2)}:{m.group(3)} {m.group(4)}")
    elif line.startswith("error: ") or line.startswith("exception:"):
        errs.append(line.strip()); full.append(line.strip())
if mode == "baseline":
    open("baseline.txt", "w").write("\n".join(errs) + "\n")
    print(f"baseline: {len(errs)} errors")
    sys.exit(0)
base = collections.Counter(l for l in open("baseline.txt").read().splitlines() if l)
seen = collections.Counter()
new = []
for e, f in zip(errs, full):
    seen[e] += 1
    if seen[e] > base[e]:
        new.append(f)
print(f"{len(errs)} errors, {len(new)} new")
for n in new[:80]:
    print("NEW", n)
PY
python3 "$(dirname "$0")/modlint.py" | grep -v "^module lint: 0"
