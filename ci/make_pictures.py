#!/usr/bin/env python3
"""Writes small test pictures for the emulator test: N portrait PNGs of different colours (0.16.0, the wallpaper library).

Usage: make_pictures.py <folder> <count>. Plain Python (zlib), no imaging library needed.
"""
import colorsys
import os
import struct
import sys
import zlib


def png(path, w, h, top, bottom):
    rows = bytearray()
    for y in range(h):
        t = y / (h - 1)
        r, g, b = (int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        rows += b"\x00" + bytes((r, g, b)) * w
    def chunk(kind, data):
        c = struct.pack(">I", len(data)) + kind + data
        return c + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(bytes(rows), 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(data)


folder, count = sys.argv[1], int(sys.argv[2])
os.makedirs(folder, exist_ok=True)
for i in range(count):
    hue = i / count
    top = tuple(int(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.75, 0.95))
    bottom = tuple(int(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.9, 0.35))
    png(os.path.join(folder, "lltest-%02d.png" % (i + 1)), 270, 600, top, bottom)
print(count)
