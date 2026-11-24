#!/usr/bin/env python3
"""Checks that each module only imports the launcher modules and Compose parts it depends on
(the one-pass type check cannot see module boundaries)."""
import os, re, sys
REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
P = "dev.liquid.launcher."
mods = {
    "core/common": ([P + "core.common"], []),
    "core/settings": ([P + "core.common", P + "core.settings"], ["androidx.compose.runtime"]),
    "core/designsystem": ([P + "core.common", P + "core.designsystem"], ["androidx.compose."]),
    "core/data": ([P + "core.common", P + "core.settings", P + "core.data"],
                  ["androidx.compose.ui.graphics", "androidx.compose.ui.geometry", "androidx.compose.ui.unit",
                   "androidx.compose.runtime.Immutable", "androidx.compose.runtime.Stable"]),
    "feature/redirect": ([P + "core.common", P + "core.settings", P + "feature.redirect"], []),
    "feature/home": ([P + "core.", P + "feature.redirect", P + "feature.home"], ["androidx.compose."]),
    "feature/settings": ([P + "core.", P + "feature.redirect", P + "feature.settings"], ["androidx.compose."]),
    "app": ([P], ["androidx.compose."]),
}
bad = 0
for mod, (own, compose) in mods.items():
    for kind in ("main",):
        root = os.path.join(REPO, mod, "src", kind, "kotlin")
        for d, _, files in os.walk(root):
            for f in files:
                if not f.endswith(".kt"):
                    continue
                path = os.path.join(d, f)
                for n, line in enumerate(open(path, encoding="utf-8"), 1):
                    m = re.match(r"\s*import\s+([\w.]+)", line)
                    if not m:
                        continue
                    imp = m.group(1)
                    if imp.startswith(P) and not any(imp.startswith(o) for o in own):
                        print(f"MODULE {path.replace(REPO + '/', '')}:{n} imports {imp} (not a dependency of {mod})"); bad += 1
                    if imp.startswith("androidx.compose.") and not any(imp.startswith(c) for c in compose):
                        print(f"MODULE {path.replace(REPO + '/', '')}:{n} imports {imp} ({mod} lacks that Compose part)"); bad += 1
                    if imp.startswith("com.kyant") and mod not in ("core/designsystem", "feature/home", "feature/settings", "app"):
                        print(f"MODULE {path.replace(REPO + '/', '')}:{n} imports {imp} (no backdrop in {mod})"); bad += 1
# A smart cast of another module's property ("if (item.video != null) item.video.kind") compiles in the one-pass
# check but not in Gradle (run 147). Flags "a.b != null" followed by "a.b." in the same line or the block it opens,
# where b is a nullable val declared in a core module.
nullable = set()
for core in ("core/common", "core/settings", "core/data", "core/designsystem"):
    for d, _, files in os.walk(os.path.join(REPO, core, "src", "main", "kotlin")):
        for f in files:
            if f.endswith(".kt"):
                for line in open(os.path.join(d, f), encoding="utf-8"):
                    for m in re.finditer(r"\bva[lr]\s+(\w+)\s*:\s*[\w.<>, ]+\?", line):
                        nullable.add(m.group(1))
cond = re.compile(r"(\b[a-z]\w*(?:\.[a-z]\w*)*)\.(\w+) != null")
for mod in ("feature/home", "feature/settings", "feature/redirect", "app"):
    for d, _, files in os.walk(os.path.join(REPO, mod, "src", "main", "kotlin")):
        for f in files:
            if not f.endswith(".kt"):
                continue
            path = os.path.join(d, f)
            lines = open(path, encoding="utf-8").read().split("\n")
            for n, line in enumerate(lines):
                for m in cond.finditer(line):
                    owner, prop = m.group(1), m.group(2)
                    if prop not in nullable:
                        continue
                    rest = line[m.end():]
                    if line.rstrip().endswith("{"):
                        rest += "\n" + "\n".join(lines[n + 1:n + 4])
                    if re.search(re.escape(owner + "." + prop) + r"\.(?!let\b|also\b|takeIf\b|run\b)\w", rest):
                        print(f"SMARTCAST {path.replace(REPO + '/', '')}:{n + 1} {owner}.{prop} is another module's property: use ?.let"); bad += 1
print(f"module lint: {bad} problem(s)")
