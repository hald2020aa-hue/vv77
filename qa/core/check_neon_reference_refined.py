#!/usr/bin/env python3
"""Static regression assertions for Neon Reference UI; not Android rendering tests."""
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[2]
K = ROOT / 'app/src/main/java/eu/kanade/tachiyomi'
D = ROOT / 'app/src/dev/res'
R = ROOT / 'app/src/main/res'
A = '{http://schemas.android.com/apk/res/android}'
def read(rel): return (K / rel).read_text()
def check(label, condition):
    assert condition, label
    print('PASS:', label)
home = read('ui/home/HomeController.kt')
storage = read('ui/home/StorageController.kt')
recents = read('ui/recents/RecentsController.kt')
check('Home uses database reading history and recorded chapter', all(x in home for x in ['getRecentMangaLimit', 'entry.chapter', 'entry.history.last_read', 'chapter.pages_left', 'ReaderActivity.newIntent']))
check('Home includes real tab routes and actual cover bindings', all(x in home for x in ['navigateTo(R.id.nav_storage)', 'navigateTo(R.id.nav_browse)', 'bindCover(manga)', 'HomeRecentCard']))
check('Storage shows measured snapshot totals', all(x in storage for x in ['StorageSummaryPanel(data', 'it.bytes', 'data?.chapterCount', 'data?.works?.size']))
check('Compact storage actions retain selection, confirmation and chapter navigation', all(x in storage for x in ['DropdownMenu(', 'toggleSelection(workId)', 'prepareDeletion(setOf(workId))', 'openWork(workId)', 'ReaderActivity.newIntent']))
check('Updates actions invoke existing job and real navigation', all(x in recents for x in ['LibraryUpdateJob.startNow(context)', 'selectedItemId = R.id.nav_browse', 'selectedItemId = R.id.nav_library']))
check('Library illustration is not used for filtered-empty results', '!hasActiveFilters' in read('ui/library/LibraryController.kt') and 'R.drawable.mv_neon_library' in read('ui/library/LibraryController.kt'))
check('Source groups derived from real adapter items', all(x in read('ui/source/SourceGroupDecoration.kt') for x in ['adapter.getItem(position)', 'is LangItem', 'is SourceItem', 'item.header?.code', 'NO_POSITION']))
check('Source viewport remains active', 'installSourceViewport(view)' in read('ui/source/BrowseController.kt'))
check('No MaterialButton background override on reading entry', 'startReadingButton.background =' not in read('ui/manga/MangaHeaderHolder.kt') and 'startReadingButton.applyNeonGradient(' in read('ui/manga/MangaHeaderHolder.kt'))
check('Settings styling keeps existing preference behavior', 'NeonPreferenceBackground' in read('ui/setting/NeonSettingsStyler.kt'))
for name in ('mv_neon_books','mv_neon_library','mv_neon_updates'):
    file = R / 'drawable-nodpi' / (name+'.webp')
    data = file.read_bytes()
    check(name+' is a WebP decorative resource', data[:4] == b'RIFF' and data[8:12] == b'WEBP' and len(data) < 300000)
strings = [{e.get('name') for e in ET.parse(R / q / 'mv_neon_refinement.xml').getroot()} for q in ('values','values-ar')]
check('Arabic and default string keys match', strings[0] == strings[1])
for file in ('layout/common_view_empty.xml','layout-h600dp/common_view_empty.xml'):
    ids = {n.get(A+'id') for n in ET.parse(D/file).getroot().iter()}
    check(file+' preserves empty state binding IDs', {'@+id/image_view','@+id/text_label','@+id/actions_container'} <= ids)
workflow = (ROOT/'.github/workflows/quality_gate.yml').read_text()
check('New branch is ARM64 only and alternatives still use original branch', "github.ref != 'refs/heads/design/neon-reference-refined'" in workflow and "if: github.ref == 'refs/heads/design/final-five'" in workflow)
print('Static checks only; Android compilation, rendering and device behavior remain separate.')
