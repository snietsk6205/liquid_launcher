#!/usr/bin/env python3
"""Prints failed unit tests from Gradle's XML results, so the CI log shows them briefly."""
import glob
import xml.etree.ElementTree as ET

failures = 0
for path in sorted(glob.glob("**/build/test-results/**/*.xml", recursive=True)):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for case in root.iter("testcase"):
        for problem in list(case.findall("failure")) + list(case.findall("error")):
            failures += 1
            print(f"FAILED {case.get('classname')}.{case.get('name')}")
            text = (problem.get("message") or "") + "\n" + (problem.text or "")
            for line in text.strip().splitlines()[:8]:
                print("    " + line)
print(f"{failures} failed test(s)")
