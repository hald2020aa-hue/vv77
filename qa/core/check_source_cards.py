#!/usr/bin/env python3
"""Static geometry and binding regression checks; not an Android layout measurement."""
from pathlib import Path
from copy import deepcopy
import importlib.util
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('mv_source_design', ROOT/'scripts/apply_design.py')
mod = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(mod)
A, P = mod.A, mod.P


def require(ok, label):
    if not ok:
        raise AssertionError(label)


def validate_source_card(root, d, edition):
    c, b, im, title, pin, latest, div = (mod.byid(root, k) for k in ('source_card','card','source_image','title','source_pin','source_latest','source_divider'))
    require(c.get(A+'layout_height') == b.get(A+'layout_height') == 'wrap_content', edition+' elastic height')
    require(b.get(A+'minHeight') == f"{d['source_height']}dp" and 56 <= d['source_height'] <= 80, edition+' compact minimum height')
    require(pin.get(A+'layout_width') == pin.get(A+'layout_height') == '48dp', edition+' pin target')
    require(latest.get(A+'minHeight') == '48dp' and latest.get(A+'layout_height') == 'wrap_content', edition+' latest target')
    require(im.get(A+'layout_width') == im.get(A+'layout_height') == f"{d['source_icon']}dp" and d['source_icon'] <= 48, edition+' compact icon')
    require(title.get(A+'layout_width') == '0dp' and title.get(A+'layout_height') == 'wrap_content' and title.get(A+'maxLines') == '2' and A+'text' not in title.attrib, edition+' dynamic title')
    require(latest.get(A+'text') == '@string/latest', edition+' translated latest')
    first, last = ('End','Start') if d['source']=='reverse' else ('Start','End')
    for node, attr, value in [(im,f'{first}_to{first}Of','parent'),(pin,f'{last}_to{last}Of','parent'),(latest,f'{last}_to{first}Of','@id/source_pin'),(div,f'{last}_to{first}Of','@id/source_latest'),(title,f'{first}_to{last}Of','@id/source_image'),(title,f'{last}_to{first}Of','@id/source_divider')]:
        require(node.get(P+'layout_constraint'+attr)==value, edition+' logical horizontal anchor '+attr)
        require(node.get(P+'layout_constraintTop_toTopOf')=='parent' and node.get(P+'layout_constraintBottom_toBottomOf')=='parent', edition+' vertical alignment')
    # Nominal dp budget (not an actual Android measure pass). Include margins, padding,
    # controls, divider, and title margins; leave space for the real title at 280dp.
    fixed = 10+12+d['source_icon']+48+d['source_latest']+1+4+12
    require(280-fixed >= 60, edition+' title space at 280dp')
    require(d['source_min_width'] >= 300, edition+' minimum grid column width')


def validate_grid(source, d, edition):
    require(f"val minCardWidth = {d['source_min_width']}.dpToPx" in source, edition+' actual grid width')
    require('if (item is LangItem) return sourcesGrid.spanCount' in source, edition+' section headings span all columns')
    require('firstInSection' not in source and 'lastInSection' not in source, edition+' single card never stretches across columns')
    require('(availableWidth / scaledMinWidth).coerceIn(1, 3)' in source and 'configuration.fontScale.coerceAtLeast(1f)' in source, edition+' responsive and font-scale-aware columns')
    require('recycler.clipBounds = viewport' in source and 'visibleBottom + gap' in source, edition+' search bar clipping retained')
    # Mirror the grid arithmetic for representative available widths/font scales.
    for width, scale in [(280,1),(360,1),(640,1),(720,1),(1000,1),(1200,1),(720,2),(1000,1.5)]:
        minimum = int(d['source_min_width']*scale)
        spans = max(1,min(3,width//minimum))
        require(spans==1 or width//spans>=minimum, edition+' column width budget')
        require(spans>=1 and spans<=3, edition+' grid limits')


def main():
    res=ROOT/'app/src/dev/res/layout'
    base=ET.parse(res/'source_item.xml').getroot()
    ids=[(n.get(A+'id'),n.tag) for n in base.iter() if n.get(A+'id')]
    browse=(ROOT/mod.KBASE/'ui/source/BrowseController.kt').read_text()
    holder=(ROOT/mod.KBASE/'ui/source/SourceHolder.kt').read_text()
    require('if (generation != iconBindGeneration) return@post' in holder and 'binding.sourceImage.setImageDrawable(null)' in holder, 'recycled icon protection')
    require('binding.sourceLatest.isVisible = source.supportsLatest' in holder and 'binding.sourceDivider.isVisible = source.supportsLatest' in holder, 'latest visibility retained')
    require('binding.title.text = sourceName' in holder and 'onLatestClick(flexibleAdapterPosition)' in holder and 'onPinClick(flexibleAdapterPosition)' in holder, 'original actions retained')
    for edition,d in mod.DESIGNS.items():
        if edition=='neon':
            root=base
            candidate=browse
        else:
            root=deepcopy(base)
            mod.source_layout(root,d)
            candidate=browse.replace('val minCardWidth = 300.dpToPx',f"val minCardWidth = {d['source_min_width']}.dpToPx")
        require(ids==[(n.get(A+'id'),n.tag) for n in root.iter() if n.get(A+'id')], edition+' original binding types')
        validate_source_card(root,d,edition)
        validate_grid(candidate,d,edition)
        print('PASS:',edition,'compact layout, logical anchors, touch targets, and grid arithmetic')
    print('PASS: source card static regression; Android measurement/device validation still required')

if __name__=='__main__':
    main()
