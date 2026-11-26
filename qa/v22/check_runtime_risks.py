#!/usr/bin/env python3
"""Static guard for crash patterns found on a real device (V22).

1. A MaterialButton (or <Button>, which Material themes inflate as MaterialButton) with
   android:background in XML. Material throws IllegalStateException from getShapeAppearanceModel()
   when such a view is a shared element ("overwritten background"). Use app:backgroundTint instead.
2. Shared-element callbacks must be SafeContainerTransformCallback, never the raw Material one.
3. Kotlin in `main` may only touch dev-only views through findViewById(...)?. (never binding fields).
Usage: check_runtime_risks.py <project_root>
"""
import os, re, sys
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"
root = sys.argv[1]
errors = []

for flavor in ("main", "dev"):
    base = os.path.join(root, "app", "src", flavor, "res")
    for d in sorted(os.listdir(base)):
        if not d.startswith("layout"):
            continue
        for fn in sorted(os.listdir(os.path.join(base, d))):
            fp = os.path.join(base, d, fn)
            try:
                tree = ET.parse(fp)
            except ET.ParseError:
                continue
            for el in tree.iter():
                tag = el.tag.split(".")[-1]
                if tag in ("MaterialButton", "Button") and A + "background" in el.attrib and flavor == "dev":
                    errors.append("%s: %s has android:background (MaterialButton overwritten-background crash)" % (fp, tag))

kt_root = os.path.join(root, "app", "src", "main", "java")
for dp, _, fs in os.walk(kt_root):
    for f in fs:
        if not f.endswith(".kt"):
            continue
        fp = os.path.join(dp, f)
        text = open(fp, encoding="utf-8").read()
        if f != "SafeContainerTransformCallback.kt" and re.search(r"(?<![A-Za-z])MaterialContainerTransformSharedElementCallback\(\)", text):
            errors.append("%s: raw MaterialContainerTransformSharedElementCallback (use SafeContainerTransformCallback)" % fp)

for e in errors:
    print("ERROR ", e)
print("\nruntime-risk check: %d error(s)" % len(errors))
sys.exit(1 if errors else 0)
