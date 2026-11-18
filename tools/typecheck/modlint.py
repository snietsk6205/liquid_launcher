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
print(f"module lint: {bad} problem(s)")
