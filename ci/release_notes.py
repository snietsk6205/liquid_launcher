#!/usr/bin/env python3
"""Prints one version's section of CHANGELOG.md, for the GitHub release notes.

Usage: release_notes.py <version> [path to CHANGELOG.md]

The section starts at the line "## <version>" (anything may follow the version on that line,
such as " — 2026-11-17") and ends before the next "## " heading. The heading itself is left
out: the release's title already names the version. Exits with 1 when the version has no
section, so a release without its changelog entry fails before anything is published.
"""
import re
import sys


def section(text: str, version: str) -> str | None:
    lines = text.splitlines()
    start = None
    heading = re.compile(r"^## +" + re.escape(version) + r"(\s|$)")
    for i, line in enumerate(lines):
        if start is None:
            if heading.match(line):
                start = i + 1
        elif line.startswith("## "):
            return "\n".join(lines[start:i]).strip() + "\n"
    if start is None:
        return None
    return "\n".join(lines[start:]).strip() + "\n"


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    version = sys.argv[1].removeprefix("v")
    path = sys.argv[2] if len(sys.argv) > 2 else "CHANGELOG.md"
    with open(path, encoding="utf-8") as f:
        text = f.read()
    body = section(text, version)
    if body is None or not body.strip():
        print(f"CHANGELOG.md has no section for {version} (a line '## {version}')", file=sys.stderr)
        return 1
    sys.stdout.write(body)
    return 0


if __name__ == "__main__":
    sys.exit(main())
