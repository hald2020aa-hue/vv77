#!/usr/bin/env python3
"""Render a specific visual edition in a disposable checkout, not the shared source branch.

Only UI resources and explicitly selected presentation files are changed. The dev flavor
is retained so original features behind BuildConfig.FLAVOR == "dev" remain active.
"""
from pathlib import Path
import argparse
import colorsys
import re
import xml.etree.ElementTree as ET

A = '{http://schemas.android.com/apk/res/android}'
P = '{http://schemas.android.com/apk/res-auto}'
T = '{http://schemas.android.com/tools}'
ET.register_namespace('android', A[1:-1]); ET.register_namespace('app', P[1:-1]); ET.register_namespace('tools', T[1:-1])

# All themes are dark on purpose: the reader's existing controls and cover overlays
# expect dark surfaces. A light theme would need a separate contrast/runtime audit.
# bg, surface, raised, border, text, muted, primary, secondary, tertiary.
DESIGNS = {
    'neon': dict(name='Neon Reference', palette=('05030F','0E0B27','1B1442','3B2A78','FFFFFF','B3ABD6','19E6FF','A14BFF','FF2FD0'), radius=16, gutter=12, source='row', source_height=68, source_min_width=300, source_icon=42, source_latest=72, hero=206, poster=112, poster_h=150, chapter=12, nav=8, icon='M12,3 L21,12 L12,21 L3,12 Z'),
    'cinema': dict(name='Cinema Nova', palette=('100D0C','211916','38271F','72523E','FFF4DF','D7BDA1','F8BD67','B96B41','E5A76A'), radius=12, gutter=16, source='row', source_height=76, source_min_width=330, source_icon=48, source_latest=80, hero=270, poster=144, poster_h=202, chapter=18, nav=12, icon='M3,5 L21,5 L21,19 L3,19 Z M8,8 L8,16 L16,12 Z'),
    'ink': dict(name='Ink Atelier', palette=('151613','23251F','32342C','5A5E4D','F5F0DE','CEC8B3','E2D5A8','A8B19B','B4A180'), radius=3, gutter=10, source='reverse', source_height=64, source_min_width=300, source_icon=38, source_latest=72, hero=172, poster=100, poster_h=145, chapter=9, nav=2, icon='M3,5 L11,7 L12,19 L3,17 Z M13,7 L21,5 L21,17 L12,19 Z'),
    'fluid': dict(name='Fluid Bloom', palette=('111C22','1C3035','28434B','4B7271','F3FFF9','BEDBD4','7EE8CD','94C7F5','D9B9ED'), radius=27, gutter=20, source='row', source_height=80, source_min_width=340, source_icon=48, source_latest=80, hero=242, poster=130, poster_h=172, chapter=17, nav=15, icon='M12,2 C16,7 21,11 21,15 A9,9 0,0 1,3 15 C3,11 8,7 12,2 Z M9,15 A3,3 0,0 0,15 15'),
    'reactor': dict(name='Reactor X', palette=('0A1213','132223','223738','3E6565','E9FFF4','AACCC5','B5F65A','5AD8C7','87A9FC'), radius=5, gutter=6, source='row', source_height=64, source_min_width=300, source_icon=36, source_latest=72, hero=184, poster=94, poster_h=130, chapter=8, nav=1, icon='M8,3 L16,3 L22,12 L16,21 L8,21 L2,12 Z M12,7 L16,12 L12,17 L8,12 Z'),
}

# Explicit palette roles shared by XML and Kotlin UI. Keep read/error/success colours
# outside the transformation unless they are one of the original brand accents.
ROLE_COLORS = {
    '05030F':0,'07041A':0,'0A0720':0,'0A0823':0,'0A0822':0,'0C0724':0,'070B16':0,'080F20':0,'0A0722':0,
    '0E0B27':1,'0B0822':1,'100B2E':1,'101A2C':1,'121C30':1,'151034':1,'150F36':1,'161036':1,
    '1B1442':2,'171040':2,'20185A':2,'1E1A4B':2,'1A2740':2,'1B2B44':2,'1A0F4A':2,'2B2354':2,'3A1B6E':2,
    '3B2A78':3,'3A2F7A':3,'7A5BFF':3,'304861':3,'36516F':3,'30405A':3,
    'F4F7FF':4,'F3F6FF':4,'E6E0FF':4,'FFFFFF':4,
    'B3ABD6':5,'7F77A8':5,'A7B4C8':5,'D9B8FF':5,
    '19E6FF':6,'19C8FF':6,'48DDF2':6,'35CEDB':6,'04D5E6':6,
    'A14BFF':7,'B26BFF':7,'7B2CFF':7,'8A3DFF':7,'3D7CFF':7,'6C8BFF':7,
    'FF2FD0':8,'FF4FA3':8,'F21C82':8,'F11DBF':8,
}
# White is retained for icons and over-image captions; exact palette role matching
# changes named white text values but does not recolor arbitrary white SVG masks.
HEX=re.compile(r'(?<![\w])#([0-9a-fA-F]{8}|[0-9a-fA-F]{6})(?![0-9a-fA-F])')
KHEX=re.compile(r'0x([0-9a-fA-F]{8})(?![0-9a-fA-F])')

UI_FILES=(
    'ui/home/HomeController.kt', 'ui/enhanced/EnhancedReadingTheme.kt',
    'ui/download/NeonDownloadPanel.kt', 'ui/download/center/DownloadCenterActivity.kt',
    'ui/setting/NeonSettingsStyler.kt', 'ui/setting/NeonSettingsDialogs.kt',
)
KBASE=Path('app/src/main/java/eu/kanade/tachiyomi')


def mapped(rgb, palette, white=False):
    rgb=rgb.upper()
    if rgb=='FFFFFF' and not white: return rgb
    if rgb in ROLE_COLORS: return palette[ROLE_COLORS[rgb]]
    # Gradients from legacy neon styles use several close dark-purple shades.
    r,g,b=(int(rgb[i:i+2],16)/255 for i in (0,2,4))
    h,s,v=colorsys.rgb_to_hsv(r,g,b)
    if 0.51 <= h <= 0.83 and s >= .30 and v <= .38:
        return palette[0 if v < .12 else 1 if v < .25 else 2]
    if 0.49 <= h <= 0.86 and s >= .40 and v >= .45:
        return palette[6 if h<.56 else 7 if h<.77 else 8]
    return rgb


def recolor(s,palette,kotlin=False):
    def change(m):
        h=m.group(1); return ('0x' if kotlin else '#')+h[:-6]+mapped(h[-6:],palette,white=kotlin)
    return (KHEX if kotlin else HEX).sub(change,s)


def setv(node,attrs):
    for key,val in attrs.items(): node.set(key,val)


def byid(root,name):
    return next(x for x in root.iter() if x.get(A+'id','').split('/')[-1]==name)


def save_xml(path,root):
    ET.indent(root, space='    ')
    ET.ElementTree(root).write(path,encoding='utf-8',xml_declaration=True)


def change_xml(path, fn):
    root=ET.parse(path).getroot(); fn(root); save_xml(path,root)


def source_layout(root,d):
    card, body, image, title, pin, latest, divider=(byid(root,x) for x in ['source_card','card','source_image','title','source_pin','source_latest','source_divider'])
    setv(card,{A+'layout_height':'wrap_content',P+'cardCornerRadius':f"{d['radius']}dp"})
    setv(body,{A+'layout_height':'wrap_content',A+'minHeight':f"{d['source_height']}dp",A+'paddingStart':'6dp',A+'paddingEnd':'6dp',A+'paddingTop':'4dp',A+'paddingBottom':'4dp'})
    # One compact horizontal row. Logical start/end mirror in Arabic. Wrap height
    # accommodates a two-line title and large text rather than clipping it.
    for n in (image,title,pin,latest,divider):
        for k in list(n.attrib):
            if k.startswith(P+'layout_constraint') or k.startswith(A+'layout_margin'): del n.attrib[k]
        setv(n,{P+'layout_constraintTop_toTopOf':'parent',P+'layout_constraintBottom_toBottomOf':'parent'})
    reverse=d['source']=='reverse'
    first='End' if reverse else 'Start'
    last='Start' if reverse else 'End'
    setv(image,{A+'layout_width':f"{d['source_icon']}dp",A+'layout_height':f"{d['source_icon']}dp",P+f'layout_constraint{first}_to{first}Of':'parent',A+'contentDescription':'@null'})
    setv(pin,{A+'layout_width':'48dp',A+'layout_height':'48dp',P+f'layout_constraint{last}_to{last}Of':'parent'})
    setv(latest,{A+'layout_width':f"{d['source_latest']}dp",A+'layout_height':'wrap_content',A+'minHeight':'48dp',A+'paddingStart':'3dp',A+'paddingEnd':'3dp',A+'maxLines':'2',A+'ellipsize':'end',P+'cornerRadius':f"{min(24,d['radius'])}dp",P+f'layout_constraint{last}_to{first}Of':'@id/source_pin'})
    setv(divider,{A+'layout_width':'1dp',A+'layout_height':'24dp',A+'layout_margin'+last:'4dp',P+f'layout_constraint{last}_to{first}Of':'@id/source_latest'})
    setv(title,{A+'layout_width':'0dp',A+'layout_height':'wrap_content',A+'layout_marginStart':'6dp',A+'layout_marginEnd':'6dp',A+'maxLines':'2',A+'textSize':'14sp',A+'gravity':'start|center_vertical',P+f'layout_constraint{first}_to{last}Of':'@id/source_image',P+f'layout_constraint{last}_to{first}Of':'@id/source_divider'})
    # When latest and its divider are GONE the title expands to the pin.


def visual_layouts(res,d):
    layout=res/'layout'
    # Different layout geometry across the real screen set, preserving every binding.
    for p in layout.glob('*.xml'):
        root=ET.parse(p).getroot()
        for n in root.iter():
            if P+'cardCornerRadius' in n.attrib: n.set(P+'cardCornerRadius',f"{d['radius']}dp")
            if P+'cornerRadius' in n.attrib and n.tag.endswith('MaterialButton'): n.set(P+'cornerRadius',f"{min(24,d['radius'])}dp")
        save_xml(p,root)
    change_xml(layout/'source_item.xml',lambda r:source_layout(r,d))
    change_xml(layout/'browse_controller.xml',lambda r:setv(byid(r,'source_recycler'),{A+'paddingStart':f"{d['gutter']}dp",A+'paddingEnd':f"{d['gutter']}dp",A+'clipToPadding':'true'}))
    def chapters(r):
        setv(r,{A+'paddingStart':f"{d['gutter']}dp",A+'paddingEnd':f"{d['gutter']}dp"})
        setv(byid(r,'chapter_title'),{A+'layout_marginTop':f"{d['chapter']}dp"})
        setv(byid(r,'chapter_scanlator'),{A+'layout_marginBottom':f"{d['chapter']}dp"})
        setv(byid(r,'download_button'),{A+'layout_width':'54dp'})
    change_xml(layout/'chapters_item.xml',chapters)
    def reader(r):
        setv(r,{A+'paddingStart':f"{min(12,d['gutter'])}dp",A+'paddingEnd':f"{min(12,d['gutter'])}dp",A+'paddingTop':f"{max(6,d['nav'])}dp",A+'paddingBottom':f"{max(6,d['nav'])}dp"})
        for name in ('start_cell','end_cell','left_chapter','right_chapter','toggle_spread'):
            setv(byid(r,name),{A+'layout_width':'48dp',A+'layout_height':'48dp'})
    change_xml(layout/'reader_nav.xml',reader)
    def nav(r):
        n=byid(r,'bottom_nav')
        setv(n,{A+'paddingStart':f"{min(10,d['gutter'])}dp",A+'paddingEnd':f"{min(10,d['gutter'])}dp",A+'paddingBottom':f"{max(4,d['nav'])}dp",P+'itemIconSize':('22dp' if d['radius']<=5 else '24dp')})
    change_xml(layout/'main_activity.xml',nav)
    def grid(r):
        card=byid(r,'card'); title=byid(r,'title'); text=byid(r,'text_layout')
        margin=max(3,d['gutter']//2)
        setv(card,{A+'layout_marginStart':f'{margin}dp',A+'layout_marginEnd':f'{margin}dp'})
        setv(text,{A+'paddingTop':f'{max(4,d["chapter"]//2)}dp'})
        setv(title,{A+'gravity':'start' if d['radius']<=5 else 'center_horizontal',A+'textSize':('14sp' if d['radius']>10 else '13sp')})
    change_xml(layout/'manga_grid_item.xml',grid)
    def detail(r):
        summary=byid(r,'manga_summary')
        setv(summary,{A+'padding':f"{d['gutter']}dp",A+'lineSpacingExtra':('6dp' if d['radius']>=20 else '3dp')})
        setv(byid(r,'start_reading_button'),{A+'minHeight':'52dp',P+'cornerRadius':f"{min(26,d['radius'])}dp"})
        setv(byid(r,'chapters_title'),{A+'textSize':('19sp' if d['radius']>10 else '16sp')})
    change_xml(layout/'manga_header_item.xml',detail)
    change_xml(layout/'download_item.xml',lambda r:setv(byid(r,'front_view'),{A+'paddingTop':f"{max(8,d['chapter'])}dp",A+'paddingBottom':f"{max(8,d['chapter'])}dp"}))
    change_xml(layout/'recent_manga_item.xml',lambda r:setv(byid(r,'card_layout'),{A+'layout_width':f"{max(54,d['poster']//2)}dp",A+'layout_height':f"{max(76,d['poster_h']//2)}dp"}))
    # Increase settings/result card whitespace for the spacious editions, compact for Ink/Reactor.
    change_xml(layout/'settings_search_controller_card.xml',lambda r:setv(r,{A+'layout_marginBottom':f"{max(3,d['gutter']//2)}dp"}))


def icon_resources(res,d):
    bg,surface,raised,border,text,muted,primary,secondary,tertiary=d['palette']
    # Each mark has a different silhouette. The original package's icon is retained for Neon.
    vector=f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:scaleX="2.5" android:scaleY="2.5" android:translateX="24" android:translateY="24">
        <path android:pathData="{d['icon']}" android:strokeColor="#{primary}" android:strokeWidth="1.4" android:strokeLineJoin="round" android:strokeLineCap="round" android:fillColor="@android:color/transparent" />
        <path android:pathData="M8,13 L12,9 L16,13" android:strokeColor="#{text}" android:strokeWidth="1.5" android:strokeLineJoin="round" android:fillColor="@android:color/transparent" />
    </group>
</vector>'''
    (res/'drawable/mv_design_mark.xml').write_text(vector+'\n')
    (res/'values/mv_design_icon.xml').write_text(f'<resources><color name="mv_design_icon_bg">#{bg}</color></resources>\n')
    for name in ('ic_launcher','ic_launcher_round'):
        q=res/'mipmap-anydpi-v26'; q.mkdir(exist_ok=True)
        (q/f'{name}.xml').write_text('''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/mv_design_icon_bg"/><foreground android:drawable="@drawable/mv_design_mark"/><monochrome android:drawable="@drawable/mv_design_mark"/></adaptive-icon>\n''')
        q=res/'mipmap-anydpi'; q.mkdir(exist_ok=True)
        # Non-adaptive fallback for launchers that request a plain icon.
        plain=vector.replace('<group android:scaleX=',f'<path android:fillColor="#{bg}" android:pathData="M0,0 H108 V108 H0 Z"/>\n    <group android:scaleX=')
        (q/f'{name}.xml').write_text(plain+'\n')


NAV_PATHS={
    'cinema': ['M3,10 L12,3 L21,10 V21 H15 V14 H9 V21 H3 Z','M3,5 H21 V19 H3 Z M7,5 V19 M17,5 V19','M12,3 A9,9 0,1 0,12,21 A9,9 0,1 0,12,3 M15,9 L9,15 L11,10 Z','M5,4 H19 V20 H5 Z M8,8 H16 M8,12 H16 M8,16 H13','M4,6 H20 V18 H4 Z M4,13 H20 M16,9 H17 M16,15 H17','M4,7 H20 M4,12 H20 M4,17 H20 M9,4 V10 M15,9 V15 M10,14 V20'],
    'ink': ['M3,11 L12,4 L21,11 M6,10 V20 H18 V10','M3,4 Q8,3 12,7 Q16,3 21,4 V19 Q16,18 12,21 Q8,18 3,19 Z M12,7 V21','M4,4 H20 V20 H4 Z M16,8 L13,13 L8,16 L11,11 Z','M5,5 H19 M5,12 H19 M5,19 H14','M4,5 H20 V20 H4 Z M4,10 H20 M8,15 H16','M4,6 H20 M4,12 H20 M4,18 H20 M8,4 V8 M16,10 V14 M11,16 V20'],
    'fluid': ['M4,11 Q12,1 20,11 V18 Q20,21 17,21 H7 Q4,21 4,18 Z M10,21 V15 H14 V21','M3,7 Q3,4 6,4 H18 Q21,4 21,7 V17 Q21,20 18,20 H6 Q3,20 3,17 Z M8,8 H16 M8,12 H16','M12,3 A9,9 0,1 0,12,21 A9,9 0,1 0,12,3 M16,8 L13,13 L8,16 L11,11 Z','M4,12 A8,8 0,1 0,12,4 M4,5 V12 H11 M12,7 V12 L16,14','M4,8 Q4,5 7,5 H17 Q20,5 20,8 V17 Q20,20 17,20 H7 Q4,20 4,17 Z M4,11 H20','M7,3 L9,6 H15 L17,3 L21,7 L18,9 V15 L21,17 L17,21 L15,18 H9 L7,21 L3,17 L6,15 V9 L3,7 Z M12,9 A3,3 0,1 0,12,15 A3,3 0,1 0,12,9'],
    'reactor': ['M3,8 L8,3 H16 L21,8 V21 H3 Z M8,21 V13 H16 V21','M3,3 H10 V21 H3 Z M14,3 H21 V21 H14 Z M5,7 H8 M16,7 H19','M12,2 L22,12 L12,22 L2,12 Z M12,6 L18,12 L12,18 L6,12 Z','M3,5 H21 V19 H3 Z M5,12 H9 L12,8 L15,16 L18,12 H21','M3,4 H21 V20 H3 Z M3,12 H21 M7,8 H10 M7,16 H10 M16,8 H18 M16,16 H18','M3,4 H21 V20 H3 Z M7,7 V17 M12,7 V17 M17,7 V17 M5,10 H9 M10,14 H14 M15,9 H19'],
}

def navigation_icons(res,design):
    names=('v19_icon_home','v14_icon_library','v14_icon_browse','v14_icon_recent','ic_storage_outline_24dp','v14_icon_settings')
    for name,path in zip(names,NAV_PATHS[design]):
        (res/'drawable'/f'{name}.xml').write_text(f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:pathData="{path}" android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFFFF" android:strokeWidth="{1.5 if design=="ink" else 2}" android:strokeLineJoin="round" android:strokeLineCap="round"/></vector>\n')

def render(root, design):
    d=DESIGNS[design]; res=root/'app/src/dev/res'; main=root/'app/src/main/res'
    assert res.is_dir() and (root/'app/build.gradle.kts').exists(), 'Not a Manga Virus checkout'
    marker=root/'.design-active'
    if marker.exists(): raise SystemExit('This checkout has already been styled. Use a fresh checkout.')
    if design=='neon':
        marker.write_text(design+'\n'); return
    palette=d['palette']
    # Keep all layout IDs, styles, resource names and view classes. Only visual
    # properties are changed. Replace literal colours in dev overlays, not source content.
    for p in res.rglob('*.xml'):
        if p.parent.name.startswith(('values','drawable','layout','color')):
            s=p.read_text(); t=recolor(s,palette)
            if p.parent.name.startswith('drawable') and p.stem.startswith(('v18_','v19_','v21_','v22_','v13_','v14_')):
                t=re.sub(r'(android:radius=")([0-9.]+)dp"',lambda m:m.group(1)+str(min(float(m.group(2)),d['radius']) if d['radius']<=12 else max(float(m.group(2)),d['radius']-3))+'dp"',t)
            if t!=s: p.write_text(t)
    # Only the presentation copies of legacy settings drawables, never shared business logic.
    for p in (main/'drawable').glob('v13_settings*.xml'):
        if not (res/'drawable'/p.name).exists(): (res/'drawable'/p.name).write_text(recolor(p.read_text(),palette))
    visual_layouts(res,d)
    # Change the home composition's geometry and cover aspect; real data/callbacks remain.
    for rel in UI_FILES:
        p=root/KBASE/rel; s=p.read_text(); t=recolor(s,palette,kotlin=True)
        if rel.endswith('HomeController.kt'):
            for a,b in [('height(206.dp)',f"height({d['hero']}.dp)"),('width(112.dp)',f"width({d['poster']}.dp)"),('height(150.dp)',f"height({d['poster_h']}.dp)"),('RoundedCornerShape(22.dp)',f"RoundedCornerShape({d['radius']}.dp)"),('RoundedCornerShape(15.dp)',f"RoundedCornerShape({d['radius']}.dp)"),('start = 14.dp, end = 14.dp',f"start = {d['gutter']}.dp, end = {d['gutter']}.dp")]:
                assert a in t,a; t=t.replace(a,b)
            # Compact editions put the navigation actions before the feature. Cinema and Fluid
            # use a full-height editorial feature, with Fluid displaying a larger cover.
            if design in ('ink','reactor'):
                a=t.index('        item {\n            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {')
                b=t.index('        item {\n            HomeGradientButton',a)
                actions=t[a:b]; t=t[:a]+t[b:]
                at=t.index('        item {\n            if (featured != null)'); t=t[:at]+actions+t[at:]
            if design=='fluid': t=t.replace('Modifier.size(width = 102.dp, height = 148.dp)','Modifier.size(width = 120.dp, height = 172.dp)')
            if design=='ink':
                # Editorial card without decorative background art.
                a=t.index('                Image(\n                    painter = painterResource(R.drawable.v22_hero_art)')
                b=t.index('                Row(',a); t=t[:a]+t[b:]
        elif rel.endswith('NeonSettingsStyler.kt'):
            def rgb(m):
                v=''.join(f'{int(x):02X}' for x in m.groups()); val=mapped(v,palette); return 'Color.rgb('+', '.join(str(int(val[i:i+2],16)) for i in (0,2,4))+')'
            t=re.sub(r'Color\.rgb\((\d+),\s*(\d+),\s*(\d+)\)',rgb,t)
        if t!=s: p.write_text(t)
    p=root/KBASE/'ui/source/BrowseController.kt'; s=p.read_text(); old='val minCardWidth = 300.dpToPx'; assert s.count(old)==1
    p.write_text(s.replace(old,f"val minCardWidth = {d['source_min_width']}.dpToPx"))
    # On-primary text must remain legible on the new pale accents.
    for p in res.glob('values*/colors.xml'):
        s=p.read_text(); t=re.sub(r'(<color name="onPrimaryTachiyomi">)#[0-9A-Fa-f]{6}(</color>)',lambda m:m.group(1)+'#'+palette[0]+m.group(2),s)
        if t!=s: p.write_text(t)
    p=root/KBASE/'ui/enhanced/EnhancedReadingTheme.kt'; s=p.read_text()
    s=re.sub(r'onPrimary = Color\(0x[0-9A-Fa-f]{8}\)',f'onPrimary = Color(0xFF{palette[0]})',s); p.write_text(s)
    p=root/KBASE/'ui/home/HomeController.kt'; s=p.read_text().replace('Text(label, color = Color.White, fontSize = 14.sp','Text(label, color = homeBackground, fontSize = 14.sp'); p.write_text(s)
    # Adaptive icon and navigation line icons are distinct, with unchanged resource IDs.
    icon_resources(res,d)
    navigation_icons(res,design)
    marker.write_text(design+'\n')

if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('design',choices=DESIGNS); parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1]); args=parser.parse_args()
    render(args.root.resolve(),args.design); print('DESIGN READY:',DESIGNS[args.design]['name'])
