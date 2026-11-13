#!/usr/bin/env python3
"""Prints "x y" (centre) of the first on-screen node whose text or description contains the given text."""
import re
import sys
import xml.etree.ElementTree as ET

try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    sys.exit(1)
needle = sys.argv[2].lower()
exact = len(sys.argv) > 3 and sys.argv[3] == "exact"
for node in root.iter("node"):
    label = (node.get("text") or "") + "|" + (node.get("content-desc") or "")
    parts = [p.lower() for p in label.split("|") if p]
    hit = any(p == needle for p in parts) if exact else needle in label.lower()
    if not hit:
        continue
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        continue
    l, t, r, b = map(int, m.groups())
    if r - l <= 0 or b - t <= 0:
        continue  # off screen (for example the closed drawer)
    print((l + r) // 2, (t + b) // 2)
    sys.exit(0)
sys.exit(1)
