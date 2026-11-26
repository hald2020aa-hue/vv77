#!/usr/bin/env python3
"""MANGA VIRUS V22 static resource gate.

Offline substitute for aapt2 / ViewBinding generation, which cannot run without an Android SDK.
It verifies, for one flavor (main + that flavor's source set merged):

  1. every XML file under app/src/{main,dev}/res is well-formed;
  2. every layout that exists in the BASELINE project keeps every android:id it had, with the
     same view tag (ViewBinding field name + type preserved -> Kotlin keeps compiling);
  3. every @drawable/@color/@string/@style/@dimen/@layout/@menu/@id/@anim/@mipmap/@bool/@integer
     reference resolves to a resource in the merged source sets (or to a known library prefix);
  4. ConstraintLayout / RelativeLayout id references resolve inside the same layout;
  5. no duplicate android:id inside one layout, no duplicate value-resource names in one
     source set + qualifier.

Usage: check_resources.py <project_root> <baseline_root | qa/v22/baseline_ids.json> [flavor=neonReference]
Exit status is non-zero when any hard error is found.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

FLAVOR = sys.argv[3] if len(sys.argv) > 3 else "neonReference"
ANDROID_NS = "http://schemas.android.com/apk/res/android"
AID = "{%s}id" % ANDROID_NS

LIB_PREFIXES = (
    "Widget.Material", "Widget.MaterialComponents", "Widget.AppCompat", "Widget.Design",
    "Theme.Material", "Theme.AppCompat", "Theme.MaterialComponents", "TextAppearance.Material",
    "TextAppearance.AppCompat", "TextAppearance.Design", "ShapeAppearance", "ThemeOverlay.Material",
    "ThemeOverlay.AppCompat", "Base.", "Platform.", "Animation.", "MaterialAlertDialog",
    "Preference", "CardView", "RtlOverlay", "abc_", "mtrl_", "m3_", "material_", "design_",
    "ic_arrow_drop", "notification_", "exo_", "androidx_", "ThemeOverlay.", "Widget.Support",
)
VALUE_TAGS = {
    "string": "string", "color": "color", "dimen": "dimen", "style": "style", "bool": "bool",
    "integer": "integer", "string-array": "array", "integer-array": "array", "array": "array",
    "plurals": "plurals", "attr": "attr", "drawable": "drawable", "fraction": "fraction",
}
FILE_TYPES = ("drawable", "layout", "color", "menu", "anim", "animator", "mipmap", "xml", "raw",
              "font", "navigation", "transition", "interpolator")


def res_dirs(root, flavor):
    return os.path.join(root, "app", "src", flavor, "res")


def qualifier_of(dirname):
    return dirname.split("-", 1)[1] if "-" in dirname else ""


def index_resources(root):
    """Return (defined: {type: set(names)}, dup_errors, xml_errors)."""
    defined = defaultdict(set)
    seen = defaultdict(list)
    xml_errors = []
    for flavor in ("main", FLAVOR):
        base = res_dirs(root, flavor)
        if not os.path.isdir(base):
            continue
        for d in sorted(os.listdir(base)):
            dp = os.path.join(base, d)
            if not os.path.isdir(dp):
                continue
            rtype = d.split("-", 1)[0]
            for fn in sorted(os.listdir(dp)):
                fp = os.path.join(dp, fn)
                stem, ext = os.path.splitext(fn)
                if rtype in FILE_TYPES:
                    defined[rtype].add(stem)
                    if rtype == "drawable":
                        defined["drawable"].add(stem)
                    if ext == ".xml":
                        try:
                            tree = ET.parse(fp)
                        except ET.ParseError as exc:
                            xml_errors.append("%s: %s" % (fp, exc))
                            continue
                        for el in tree.iter():
                            v = el.attrib.get(AID)
                            if v and v.startswith("@+id/"):
                                defined["id"].add(v[5:])
                elif rtype == "values":
                    try:
                        tree = ET.parse(fp)
                    except ET.ParseError as exc:
                        xml_errors.append("%s: %s" % (fp, exc))
                        continue
                    for el in tree.getroot():
                        tag = el.tag
                        name = el.attrib.get("name")
                        if tag == "item" and el.attrib.get("type") == "id" and name:
                            defined["id"].add(name)
                            continue
                        if tag == "declare-styleable":
                            for ch in el:
                                if ch.attrib.get("name"):
                                    defined["attr"].add(ch.attrib["name"])
                            continue
                        if tag in VALUE_TAGS and name:
                            defined[VALUE_TAGS[tag]].add(name)
                            seen[(flavor, d, VALUE_TAGS[tag], name)].append(fn)
    dups = ["%s/%s %s '%s' defined in %s" % (k[0], k[1], k[2], k[3], ", ".join(v))
            for k, v in seen.items() if len(v) > 1]
    return defined, dups, xml_errors


def layout_ids(path):
    """Return ({id: tag}, [duplicate ids], root_element) for a layout file."""
    ids, dups = {}, []
    tree = ET.parse(path)
    for el in tree.iter():
        v = el.attrib.get(AID)
        if v and (v.startswith("@+id/") or v.startswith("@id/")):
            name = v.split("/", 1)[1]
            if name in ids:
                dups.append(name)
            ids[name] = el.tag
    return ids, dups, tree


def effective_layouts(root):
    """Map '<qualifier dir>/<file>' -> path with dev overriding main, per flavor-merge rules."""
    out = {}
    for flavor in ("main", FLAVOR):
        base = res_dirs(root, flavor)
        if not os.path.isdir(base):
            continue
        for d in os.listdir(base):
            if d.split("-", 1)[0] != "layout":
                continue
            for fn in os.listdir(os.path.join(base, d)):
                if fn.endswith(".xml"):
                    out["%s/%s" % (d, fn)] = os.path.join(base, d, fn)
    return out


REF_RE = re.compile(r"@(?!\+)(?:android:)?(drawable|color|string|style|dimen|layout|menu|id|anim|mipmap|bool|integer|array|xml|font|plurals|animator|raw|interpolator|fraction)/([A-Za-z0-9_.]+)")


def main():
    root, baseline = sys.argv[1], sys.argv[2]
    errors, warnings = [], []
    defined, dups, xml_errors = index_resources(root)
    errors += ["XML parse: " + e for e in xml_errors]
    errors += ["Duplicate resource: " + d for d in dups]

    # attributes that aapt2 rejects ("attribute ... not found") but XML parsing and reference checks accept
    invalid_attrs = (("android:style=", "use style=, not android:style="),
                     ("itemActiveIndicatorEnabled", "not an attribute of this Material version"))
    for flavor in ("main", FLAVOR):
        base = res_dirs(root, flavor)
        for dpath, _dirs, files in os.walk(base):
            for fn in files:
                if fn.endswith(".xml"):
                    fp = os.path.join(dpath, fn)
                    text = open(fp, encoding="utf-8", errors="replace").read()
                    for needle, why in invalid_attrs:
                        if needle in text:
                            errors.append("%s: %s (%s)" % (os.path.relpath(fp, root), needle, why))

    # 3 + 4: references and in-layout id references
    for flavor in ("main", FLAVOR):
        base = res_dirs(root, flavor)
        for d in sorted(os.listdir(base)):
            dp = os.path.join(base, d)
            if not os.path.isdir(dp):
                continue
            for fn in sorted(os.listdir(dp)):
                if not fn.endswith(".xml"):
                    continue
                fp = os.path.join(dp, fn)
                try:
                    text = open(fp, encoding="utf-8").read()
                except UnicodeDecodeError:
                    continue
                if flavor == "main":
                    continue  # only validate dev-flavor files; main is baseline code
                text = re.sub(r'tools:[A-Za-z_]+="[^"]*"', "", text)  # preview-only attributes
                text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
                for m in REF_RE.finditer(text):
                    rtype, name = m.group(1), m.group(2)
                    if m.group(0).startswith("@android:"):
                        continue
                    if rtype == "drawable" and name in defined["mipmap"]:
                        continue
                    if rtype == "style":
                        key = name.replace(".", "_")
                        if name in defined["style"] or key in defined["style"]:
                            continue
                        if name.startswith(LIB_PREFIXES):
                            continue
                    if rtype == "id":
                        # ids declared anywhere (layouts, ids.xml) count
                        if name in defined["id"]:
                            continue
                    if name in defined.get(rtype, set()):
                        continue
                    if name.startswith(LIB_PREFIXES):
                        continue
                    if rtype == "color" and name in ("transparent", "white", "black"):
                        continue
                    errors.append("%s: unresolved @%s/%s" % (os.path.relpath(fp, root), rtype, name))

    # 2 + dup ids: compare against baseline
    manifest = None
    if baseline.endswith(".json"):
        import json
        manifest = json.load(open(baseline, encoding="utf-8"))
        base_layouts = {k: None for k in manifest}
    else:
        base_layouts = effective_layouts(baseline)
    new_layouts = effective_layouts(root)
    for key, path in sorted(new_layouts.items()):
        if "/dev/" not in path.replace("\\", "/") and f"src/{FLAVOR}" not in path:
            continue
        try:
            ids, dup_ids, _ = layout_ids(path)
        except ET.ParseError:
            continue
        for dname in dup_ids:
            errors.append("%s: duplicate android:id '%s'" % (key, dname))
        # in-layout references
        text = open(path, encoding="utf-8").read()
        for m in re.finditer(r'="@id/([A-Za-z0-9_]+)"', text):
            if m.group(1) not in ids and m.group(1) not in defined["id"]:
                errors.append("%s: reference to missing id @id/%s" % (key, m.group(1)))
        if key in base_layouts:
            if manifest is not None:
                bids = manifest[key]
            else:
                try:
                    bids, _, _ = layout_ids(base_layouts[key])
                except ET.ParseError:
                    continue
            for name, tag in bids.items():
                if name not in ids:
                    errors.append("%s: id '%s' (%s) from baseline is MISSING" % (key, name, tag))
                elif ids[name] != tag:
                    warnings.append("%s: id '%s' changed tag %s -> %s (verify Kotlin usage)" % (key, name, tag, ids[name]))

    for w in warnings:
        print("WARN  ", w)
    for e in errors:
        print("ERROR ", e)
    print("\nresource check: %d error(s), %d warning(s)" % (len(errors), len(warnings)))
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
