#!/usr/bin/env python3
"""Prints the texts on screen (from a uiautomator dump) with their positions, top to bottom."""
import re
import sys
import xml.etree.ElementTree as ET

try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception as error:  # the dump can be missing or cut off
    print(f"  (no ui dump: {error})")
    sys.exit(0)

rows = []
for node in root.iter("node"):
    label = node.get("text") or node.get("content-desc") or ""
    if not label.strip():
        continue
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not match:
        continue
    left, top, right, bottom = map(int, match.groups())
    rows.append((top, left, right - left, bottom - top, label.replace("\n", " / ")))

limit = int(sys.argv[2]) if len(sys.argv) > 2 else 40
for top, left, width, height, label in sorted(rows)[:limit]:
    print(f"  y={top:<5} x={left:<5} {width}x{height:<5} {label[:70]}")
if len(rows) > limit:
    print(f"  ... {len(rows) - limit} more")
