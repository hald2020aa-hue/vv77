#!/usr/bin/env python3
"""Variant themes.xml files are full copies of main's (a style cannot be patched per item).
Only the block between <!-- <variant>:begin --> and <!-- <variant>:end --> may differ, so main's theme edits
are never silently lost. Usage: check_theme_copies.py <repo root>"""
import re, sys
from pathlib import Path
root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
main = (root / "app/src/main/res/values/themes.xml").read_text(encoding="utf-8")
bad = 0
for variant, name in (("cinemaNova", "cinema"),):
    p = root / f"app/src/{variant}/res/values/themes.xml"
    if not p.exists():
        continue
    stripped = re.sub(r"[ \t]*<!-- %s:begin -->.*?<!-- %s:end -->\n" % (name, name), "", p.read_text(encoding="utf-8"), flags=re.S)
    if stripped != main:
        print(f"FAIL: {p} differs from main themes.xml outside the {name}:begin/end block")
        bad += 1
    else:
        print(f"PASS: {p} equals main themes.xml plus the marked block")
sys.exit(1 if bad else 0)
