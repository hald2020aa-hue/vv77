#!/usr/bin/env python3
"""Cinema Nova layout overlays are *derived* from main's layouts by exact, asserted string replacements, so a
change in main either flows through or fails loudly here instead of leaving a stale copy.
  derive_cinema_layouts.py <root> --write   regenerate the overlays
  derive_cinema_layouts.py <root> --check   fail if an overlay differs from what main would produce"""
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
MODE = sys.argv[2] if len(sys.argv) > 2 else "--check"

# file -> list of (old, new); each `old` must occur exactly once in main's file
OVERLAYS = {
    "layout/manga_grid_item.xml": [
        # poster card: nearly square corners, no outline, lifted off the page
        ('app:strokeWidth="1dp" app:cardCornerRadius="20dp" app:cardElevation="2dp"',
         'app:strokeWidth="0dp" app:cardCornerRadius="6dp" app:cardElevation="6dp"'),
        ('android:layout_marginStart="9dp" android:layout_marginTop="9dp" android:layout_marginEnd="9dp" app:layout_constraintBottom_toTopOf="@+id/text_layout"',
         'android:layout_marginStart="6dp" android:layout_marginTop="6dp" android:layout_marginEnd="6dp" app:layout_constraintBottom_toTopOf="@+id/text_layout"'),
        # taller, opaque scrim so the title always reads over bright covers
        ('android:layout_height="125sp" android:alpha="0.75"', 'android:layout_height="170sp" android:alpha="1"'),
        # credit-roll title
        ('android:textColor="@android:color/white" android:textSize="13sp"',
         'android:textColor="@android:color/white" android:textSize="14sp" android:textStyle="bold"'),
        # "continue watching" style progress line along the poster's bottom edge
        ('android:layout_height="4dp" android:visibility="gone" android:max="100"',
         'android:layout_height="3dp" android:visibility="gone" android:max="100"'),
        ('android:layout_marginStart="12dp" android:layout_marginEnd="12dp" app:layout_constraintBottom_toBottomOf="@id/card"',
         'android:layout_marginStart="6dp" android:layout_marginEnd="6dp" app:layout_constraintBottom_toBottomOf="@id/card"'),
    ],
    "layout/manga_header_item.xml": [
        # larger poster, lifted, no outline
        ('app:strokeWidth="1dp" app:strokeColor="@color/v10_neon_border_soft" app:cardElevation="2dp"',
         'app:strokeWidth="0dp" app:strokeColor="@color/v10_neon_border_soft" app:cardElevation="12dp"'),
        ('app:layout_constraintWidth_percent="0.25"', 'app:layout_constraintWidth_percent="0.36"'),
        # title as a headline
        ('android:text="@string/title" android:textIsSelectable="false" android:textSize="20sp"',
         'android:text="@string/title" android:textIsSelectable="false" android:textSize="24sp"'),
        # longer fade from backdrop into the page
        ('android:layout_height="300dp" android:background="@drawable/gradient_shape"',
         'android:layout_height="360dp" android:background="@drawable/gradient_shape"'),
    ],
}

def derive(rel):
    text = (ROOT / "app/src/main/res" / rel).read_text(encoding="utf-8")
    for old, new in OVERLAYS[rel]:
        n = text.count(old)
        if n != 1:
            sys.exit(f"FAIL: {rel}: expected exactly 1 occurrence of {old[:70]!r}, found {n}")
        text = text.replace(old, new)
    return text

bad = 0
for rel in OVERLAYS:
    out = ROOT / "app/src/cinemaNova/res" / rel
    want = derive(rel)
    if MODE == "--write":
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(want, encoding="utf-8")
        print("wrote", out)
    else:
        if not out.exists() or out.read_text(encoding="utf-8") != want:
            print(f"FAIL: {out} is stale; run derive_cinema_layouts.py . --write")
            bad += 1
        else:
            print(f"PASS: {out} matches main plus the asserted replacements")
sys.exit(1 if bad else 0)
