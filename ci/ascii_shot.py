#!/usr/bin/env python3
"""Prints a screenshot as a small block of characters, dark to light, to eyeball a layout in a log."""
import sys

try:
    from PIL import Image
except ImportError:
    print("  (Pillow not installed, no thumbnail)")
    sys.exit(0)

RAMP = " .:-=+*#%@"
COLUMNS, ROWS = 46, 30

try:
    image = Image.open(sys.argv[1]).convert("L").resize((COLUMNS, ROWS))
except Exception as error:
    print(f"  (no screenshot: {error})")
    sys.exit(0)

pixels = list(image.tobytes())
for row in range(ROWS):
    line = pixels[row * COLUMNS:(row + 1) * COLUMNS]
    print("  |" + "".join(RAMP[min(value * len(RAMP) // 256, len(RAMP) - 1)] for value in line) + "|")
