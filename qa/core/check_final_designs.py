#!/usr/bin/env python3
"""Render all five designs and validate their real XML structure and isolation.
This is static QA; Gradle and on-device testing are separate gates.
"""
from pathlib import Path
import hashlib
import importlib.util
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
SPEC=importlib.util.spec_from_file_location('mv_design',ROOT/'scripts/apply_design.py')
mod=importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(mod)
A=mod.A; P=mod.P
BASE=ROOT/'app/src/dev/res'
KBASE=ROOT/mod.KBASE

def require(condition,label):
    if not condition: raise AssertionError(label)


def layouts(res):
    return {p.relative_to(res).as_posix():[(x.get(A+'id'),x.tag) for x in ET.parse(p).getroot().iter() if x.get(A+'id')] for p in res.rglob('*.xml') if p.parent.name.startswith('layout')}


def menu_ids(res):
    return {p.name:[n.get(A+'id') for n in ET.parse(p).getroot().iter('item')] for p in (res/'menu').glob('*.xml')}


def digests(root):
    return {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}


def run_check(root, script,*args):
    proc=subprocess.run([sys.executable,str(ROOT/script),*map(str,args)],cwd=root,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    require(proc.returncode==0,f'{script}: {proc.stdout[-5000:]}')


def main():
    ids=layouts(BASE); menus=menu_ids(BASE)
    allowed=set(mod.UI_FILES)|{'ui/source/BrowseController.kt'}
    known=['neon','cinema','ink','fluid','reactor']
    require(list(mod.DESIGNS)==known,'five named designs')
    require(len({tuple(d['palette']) for d in mod.DESIGNS.values()})==5,'different palettes')
    require(len({(d['radius'],d['gutter'],d['hero'],d['poster'],d['source_height']) for d in mod.DESIGNS.values()})==5,'different geometry')
    gradle=(ROOT/'app/build.gradle.kts').read_text()
    require('applicationIdSuffix = ".enhanced" + designSuffixes.getValue(design)' in gradle,'build package separation')
    require('applicationIdSuffix = ".debugJ2K"' in gradle,'existing debug suffix')
    require('"neon" to ""' in gradle,'Neon keeps the original identity')
    for n in known[1:]: require(f'"{n}" to ".{n}"' in gradle,f'{n} unique package suffix')
    work=(ROOT/'.github/workflows/quality_gate.yml').read_text()
    for name in known[1:]: require(f'id: {name},' in work,f'{name} matrix entry')
    require('fail-fast: false' in work and 'needs: android-debug-and-tests' in work,'separate CI builds')
    require('design/final-five' in work,'final branch selection')
    # Common fixes must be present before rendering variants.
    http=(KBASE/'ui/reader/loader/HttpPageLoader.kt').read_text()
    require('if (isCurrent) emit(request.page)' in http and 'queuedByPage[request.page] !== request' in http,'stale page guard')
    require('priority == 0 && focusedPage >= 0' in http,'stale speculative offers prevented')
    home=(KBASE/'ui/home/HomeController.kt').read_text()
    require('itemsIndexed(genres)' in home and 'it.url.hashCode()' not in home,'stable home keys and indexed genres')
    require('LocalLayoutDirection provides LocalLayoutDirection.current' in home,'home layout respects current direction')
    storage=(KBASE/'ui/home/StorageController.kt').read_text()
    for tag in ['new:', 'download:', 'work:']: require('"'+tag+'${' in storage,f'storage key {tag}')
    require('string.codePointAt(0)' in (KBASE/'ui/library/LibraryCategoryAdapter.kt').read_text(),'no full Unicode array for first letter')
    require('DATABASE_VERSION = 21' in (KBASE/'data/database/DbOpenCallback.kt').read_text(),'database schema 21 unchanged')
    prefs=(KBASE/'data/preference/PreferencesHelper.kt').read_text()
    require('"MangaVirus-${BuildConfig.DESIGN_EDITION}"' in prefs and 'BuildConfig.DESIGN_EDITION == "neon"' in prefs,'independent default folders with original Neon path')
    require('File(defaultStorageRoot, "backup")' in prefs,'independent default backups')
    require('preferences.defaultDownloadsFolder' in (KBASE/'ui/setting/SettingsDownloadController.kt').read_text(),'download picker uses edition-specific defaults')
    require('DESIGN_EDITION' in gradle,'build edition identity field')
    require('?.isLowRamDevice ?: true' in (KBASE/'data/image/coil/CoilSetup.kt').read_text(),'conservative memory fallback')
    require('private val plainMarkwon by lazy' in (KBASE/'ui/manga/MangaHeaderHolder.kt').read_text(),'reuses description parser')
    basekt=digests(KBASE)
    actual_layout_hashes=[]; icon_hashes=[]
    for edition in known:
        with tempfile.TemporaryDirectory(prefix='mv-design-qa-') as td:
            r=Path(td)
            shutil.copytree(ROOT/'app/src/main/res',r/'app/src/main/res')
            shutil.copytree(BASE,r/'app/src/dev/res')
            # All Kotlin files are copied to verify that the generator touches only the
            # allowlisted UI files. Business/network/translation/deletion code cannot change.
            shutil.copytree(KBASE,r/mod.KBASE)
            shutil.copy2(ROOT/'app/build.gradle.kts',r/'app/build.gradle.kts')
            mod.render(r,edition)
            res=r/'app/src/dev/res'
            require((r/'.design-active').read_text().strip()==edition,edition+' marker')
            require(layouts(res)==ids,edition+' preserves every layout view binding ID and type')
            require(menu_ids(res)==menus,edition+' preserves navigation destinations and order')
            changed={k for k,v in digests(r/mod.KBASE).items() if basekt[k]!=v}
            require(changed<=allowed,edition+' touched non-UI Kotlin: '+str(changed-allowed))
            if edition=='neon': require(not changed,edition+' reference unchanged')
            else: require(len(changed)>=4,edition+' has actual UI changes')
            for q in ('values','values-ar'):
                strings=ET.parse(res/q/'strings.xml').getroot()
                for n in ('app_name','app_short_name'):
                    require(next(x.text for x in strings if x.get('name')==n)=='فيروس مانجا',f'{edition} {q} app label')
            # Distinct geometry is verified in generated Android resources, not just in specs.
            sr=ET.parse(res/'layout/source_item.xml').getroot()
            actual_layout_hashes.append(hashlib.sha256((res/'layout/source_item.xml').read_bytes()).hexdigest())
            if edition!='neon':
                require(mod.byid(sr,'source_pin').get(A+'layout_width')=='48dp',edition+' source pin touch target')
                require(mod.byid(sr,'source_latest').get(A+'layout_height')=='48dp',edition+' latest touch target')
                require(mod.byid(sr,'source_card').get(A+'layout_height')==str(mod.DESIGNS[edition]['source_height'])+'dp',edition+' card geometry')
                icon_hashes.append(hashlib.sha256((res/'drawable/mv_design_mark.xml').read_bytes()).hexdigest())
                nav=ET.parse(res/'layout/reader_nav.xml').getroot()
                require(mod.byid(nav,'left_chapter').get(A+'layout_width')=='48dp',edition+' reader touch target')
            # Cross-resource validation uses the same resource gate as GitHub Actions.
            run_check(r,Path('qa/v22/check_resources.py'),r,ROOT/'qa/v22/baseline_ids.json')
            run_check(r,Path('qa/v22/check_runtime_risks.py'),r)
            try: mod.render(r,edition)
            except SystemExit: pass
            else: raise AssertionError('render twice must fail')
            print(f'PASS: {mod.DESIGNS[edition]["name"]} — XML IDs, navigation, resource references, labels, UI isolation and crash patterns',flush=True)
    require(len(set(actual_layout_hashes))==5,'5 different rendered source layouts')
    require(len(set(icon_hashes))==4,'4 independent alternative launcher marks')
    print('PASS: five-edition static verification. Android APK/device verification remains separate.')

if __name__=='__main__': main()
