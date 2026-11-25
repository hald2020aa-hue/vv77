#!/usr/bin/env python3
"""V21 source-level checks for the ten-screen redesign; not an APK/device test."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'
checks = 0

def test(ok, label):
    global checks
    if not ok:
        raise AssertionError(label)
    checks += 1
    print('PASS:', label)

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

# Shared V21 resources used by the ten-screen dev experience.
for drawable in [
    'v21_screen_background.xml', 'v21_surface_card.xml', 'v21_header_banner.xml',
    'v21_chip.xml', 'v21_reader_panel.xml', 'v21_bottom_navigation.xml',
]:
    path = ROOT / 'app/src/dev/res/drawable' / drawable
    test(path.is_file(), f'V21 shared visual resource exists: {drawable}')
    ET.parse(path)

screens = {
    'downloads': 'app/src/main/java/eu/kanade/tachiyomi/ui/download/NeonDownloadPanel.kt',
    'chapters': 'app/src/dev/res/layout/chapters_item.xml',
    'updates': 'app/src/dev/res/layout/recent_manga_item.xml',
    'settings': 'app/src/main/java/eu/kanade/tachiyomi/ui/setting/NeonSettingsStyler.kt',
    'home': 'app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeController.kt',
    'reader': 'app/src/dev/res/layout/reader_activity.xml',
    'browse': 'app/src/dev/res/layout/source_item.xml',
    'details': 'app/src/dev/res/layout/manga_header_item.xml',
    'search': 'app/src/dev/res/layout/source_global_search_controller_card_item.xml',
    'library': 'app/src/dev/res/layout/manga_grid_item.xml',
}
for name, path in screens.items():
    test((ROOT / path).is_file(), f'{name} has a dev-specific visual implementation')

home = read(screens['home'])
for pattern, label in [
    ('db.getFavoriteMangas()', 'home uses the existing library database'),
    ('db.getRecentMangaLimit', 'home uses the real reading history'),
    ('sourceManager.getCatalogueSources()', 'home counts installed catalogue sources'),
    ('loadManga(manga)', 'home renders real manga covers'),
    ('it.getGenres().orEmpty()', 'home genre chips are derived from actual manga metadata'),
    ('HomeGenreChip(genre', 'home renders the data-derived genre chips'),
    ('MangaDetailsController(manga)', 'home opens the original manga details controller'),
]:
    test(pattern in home, label)
for forbidden in ['Solo Leveling', 'Fake title', 'mockManga', 'Sample manga']:
    test(forbidden not in home, f'home does not inject sample manga data ({forbidden})')

downloads = read(screens['downloads'])
for pattern in ['Download.State.DOWNLOADING', 'Download.State.QUEUE', 'onPause', 'onResume', 'onDelete', 'onPrioritize', 'loadManga(row.download.manga)']:
    test(pattern in downloads, f'download queue keeps live behavior: {pattern}')

browse_controller = read('app/src/main/java/eu/kanade/tachiyomi/ui/source/BrowseController.kt')
for pattern in ['GridLayoutManager(view.context, 2)', 'adapter?.getItem(position) is LangItem) 2 else 1', 'SourceAdapter(this)', 'SourcePresenter(this)']:
    test(pattern in browse_controller, f'browse preserves original source wiring: {pattern}')
source_xml = ET.parse(ROOT / screens['browse']).getroot()
source_card = next(n for n in source_xml.iter() if n.attrib.get(ANDROID + 'id') == '@+id/source_card')
test(source_card.attrib.get(ANDROID + 'layout_height') == '154dp', 'browse cards use the compact two-column layout')
for view_id in ['source_image', 'title', 'source_latest', 'source_pin', 'source_divider']:
    test(any(n.attrib.get(ANDROID + 'id') == '@+id/' + view_id for n in source_xml.iter()), f'browse retains source binding {view_id}')

search_holder = read('app/src/main/java/eu/kanade/tachiyomi/ui/source/globalsearch/GlobalSearchHolder.kt')
for pattern in ['LinearLayoutManager(view.context)', 'binding.recycler.isNestedScrollingEnabled = true', 'binding.recycler.adapter = mangaAdapter']:
    test(pattern in search_holder, f'global search uses full-width result rows: {pattern}')
search_xml = ET.parse(ROOT / screens['search']).getroot()
test(any(n.attrib.get(ANDROID + 'id') == '@+id/itemImage' for n in search_xml.iter()), 'search row retains actual cover binding')
test(any(n.attrib.get(ANDROID + 'id') == '@+id/title' for n in search_xml.iter()), 'search row retains title binding')
test(any(n.attrib.get(ANDROID + 'id') == '@+id/gradient' for n in search_xml.iter()), 'search row retains cover fade overlay')
chapter_header = read('app/src/dev/res/layout/chapter_header_item.xml')
test('@drawable/v21_chip' in chapter_header, 'chapter filter header uses the shared V21 chip style')

# Core reading, chapter and details bindings remain intact.
for screen_name, path, ids in [
    ('details', screens['details'], ['manga_cover', 'title', 'manga_author', 'manga_status', 'manga_source', 'favorite_button', 'track_button', 'share_button', 'manga_summary']),
    ('reader', screens['reader'], ['viewer_container', 'page_number', 'toolbar', 'touch_view', 'reader_nav', 'chapters_sheet']),
    ('chapters', screens['chapters'], ['chapter_title', 'chapter_scanlator', 'download_button']),
]:
    xml = ET.parse(ROOT / path).getroot()
    ids_found = {n.attrib.get(ANDROID + 'id') for n in xml.iter()}
    for view_id in ids:
        test('@+id/' + view_id in ids_found, f'{screen_name} keeps {view_id} binding')

recents_controller = read('app/src/main/java/eu/kanade/tachiyomi/ui/recents/RecentsController.kt')
for pattern, label in [
    ('presenter.viewType == RecentsViewType.Updates', 'update banner is limited to the existing Updates view'),
    ('updates.size.toString()', 'update chapter count comes from real update rows'),
    ('updates.mapNotNull { it.mch.manga.id }.distinct().size', 'update title count uses distinct real manga IDs'),
    ('item.downloadInfo.any { it.isDownloaded }', 'downloaded update count uses live download status'),
]:
    test(pattern in recents_controller, label)
for path in ['app/src/dev/res/layout/recents_controller.xml', 'app/src/main/res/layout/recents_controller.xml']:
    xml = ET.parse(ROOT / path).getroot()
    ids = {n.attrib.get(ANDROID + 'id') for n in xml.iter()}
    for view_id in ['recents_summary', 'recents_summary_title', 'recents_summary_subtitle', 'recents_summary_chapters', 'recents_summary_titles', 'recents_summary_downloaded']:
        test('@+id/' + view_id in ids, f'{path} keeps shared binding {view_id}')

library_controller = read('app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryController.kt')
for pattern, label in [
    ('updateLibrarySummary()', 'library summary refresh is wired to real library updates'),
    ('items.count { it.manga.status == SManga.COMPLETED }', 'completed count comes from actual manga status'),
    ('items.count { it.manga.read > 0 && it.manga.unread > 0 }', 'reading count comes from actual read/unread totals'),
    ('items.count { it.downloadCount > 0 }', 'download count comes from the existing download cache count'),
]:
    test(pattern in library_controller, label)
for path in ['app/src/dev/res/layout/library_controller.xml', 'app/src/main/res/layout/library_controller.xml']:
    xml = ET.parse(ROOT / path).getroot()
    ids = {n.attrib.get(ANDROID + 'id') for n in xml.iter()}
    for view_id in ['library_stat_total', 'library_stat_completed', 'library_stat_reading', 'library_stat_downloaded']:
        test('@+id/' + view_id in ids, f'{path} keeps shared binding {view_id}')

settings = read(screens['settings'])
test('PreferenceGroupAdapter' in settings, 'settings retain the original AndroidX preference adapter')
test('RippleDrawable' in settings and 'MaterialSwitch' in settings, 'settings styling does not replace original controls')

# Source providers, chapter loading, translation and persistence code must remain unchanged.
protected = [
    'app/src/main/java/eu/kanade/tachiyomi/source/Source.kt',
    'app/src/main/java/eu/kanade/tachiyomi/source/CatalogueSource.kt',
    'app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsPresenter.kt',
    'app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt',
    'app/src/main/java/eu/kanade/tachiyomi/ui/reader/translation/OnDeviceChapterTranslator.kt',
    'app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadManager.kt',
]
for path in protected:
    test((ROOT / path).is_file(), f'protected functional module remains present: {path}')

# V21 app identity and release safety.
gradle = read('app/build.gradle.kts')
test('applicationIdSuffix = ".enhanced"' in gradle, 'dev package remains isolated from stable package')
test('versionNameSuffix = "-enhanced-v21-dev"' in gradle, 'V21 development build is identifiable')
test('versionCode = AndroidVersions.versionCode + 3' in gradle, 'V21 version code advances monotonically')
for file, expected in [
    ('app/src/dev/res/values/strings.xml', 'MANGA VIRUS V21'),
    ('app/src/dev/res/values-ar/strings.xml', 'فيروس مانجا V21'),
]:
    test(expected in read(file), f'{file} identifies V21')

# Parse every XML resource in the dev flavor and reject duplicate IDs per file.
xml_count = 0
for path in (ROOT / 'app/src/dev/res').rglob('*.xml'):
    root = ET.parse(path).getroot()
    xml_count += 1
    ids = [node.attrib.get(ANDROID + 'id') for node in root.iter() if node.attrib.get(ANDROID + 'id')]
    test(len(ids) == len(set(ids)), f'no duplicate IDs in {path.relative_to(ROOT)}')
test(xml_count >= 130, 'all dev XML resources were parsed')

print(f'V21 VISUAL ALIGNMENT CHECKS: {checks}/{checks} PASS — source checks only; no APK or device validation.')
