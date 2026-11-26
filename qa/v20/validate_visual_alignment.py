#!/usr/bin/env python3
"""Legacy ten-screen gate retained for the V21 source; not an APK/device test."""
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

palette = read('app/src/dev/res/values/neon_v19_colors.xml')
for color, value in [
    ('v19_bg', '#05050D'), ('v19_cyan', '#31E4FF'), ('v19_purple', '#A94DFF'),
    ('v19_pink', '#FF32CB'), ('v19_success', '#36E2B0'),
]:
    test(f'name="{color}"' in palette and value in palette, f'V20 shared palette defines {color}')

# Ten reference areas: existing real screens and adapters remain the source of content.
screens = {
    'downloads': 'app/src/main/java/eu/kanade/tachiyomi/ui/download/NeonDownloadPanel.kt',
    'chapter_list': 'app/src/dev/res/layout/chapters_item.xml',
    'updates': 'app/src/dev/res/layout/recent_manga_item.xml',
    'settings': 'app/src/main/java/eu/kanade/tachiyomi/ui/setting/NeonSettingsStyler.kt',
    'home': 'app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeController.kt',
    'reader': 'app/src/dev/res/layout/reader_activity.xml',
    'browse': 'app/src/dev/res/layout/source_item.xml',
    'manga_details': 'app/src/dev/res/layout/manga_header_item.xml',
    'search': 'app/src/dev/res/layout/source_global_search_controller_card_item.xml',
    'library': 'app/src/dev/res/layout/manga_grid_item.xml',
}
for name, path in screens.items():
    test((ROOT / path).is_file(), f'{name} screen has a dev-specific visual implementation')

home = read(screens['home'])
for pattern, label in [
    ('db.getFavoriteMangas()', 'home loads real library records'),
    ('db.getRecentMangaLimit', 'home loads real reading history'),
    ('sourceManager.getCatalogueSources()', 'home counts installed catalogue sources'),
    ('bindCover(manga)', 'home displays real cover images'),
    ('MangaDetailsController(manga)', 'home opens existing manga details'),
    ('DownloadCenterActivity::class.java', 'home opens the existing download center'),
    ('HomeMangaPoster', 'home uses poster cards for actual manga records'),
]:
    test(pattern in home, label)
for forbidden in ['Sample manga', 'Solo Leveling', 'Fake title', 'mockManga']:
    test(forbidden not in home, f'home has no hard-coded sample content ({forbidden})')

downloads = read(screens['downloads'])
for pattern in ['Download.State.DOWNLOADING', 'Download.State.QUEUE', 'onPause', 'onResume', 'onDelete', 'onPrioritize', 'bindCover(row.download.manga)']:
    test(pattern in downloads, f'download screen preserves live queue behavior: {pattern}')

browse = read('app/src/main/java/eu/kanade/tachiyomi/ui/source/BrowseController.kt')
for pattern in ['GridLayoutManager(view.context, 2)', 'adapter?.getItem(position) is LangItem) 2 else 1']:
    test(pattern in browse, f'browse grid behavior is wired: {pattern}')
test('if (view.resources.getBoolean(R.bool.v4_neon_mode))' in browse and 'LinearLayoutManagerAccurateOffset(view.context)' in browse, 'two-column source grid is limited to dev neon flavor')
source_xml = ET.parse(ROOT / screens['browse']).getroot()
source_card = next(n for n in source_xml.iter() if n.attrib.get(ANDROID + 'id') == '@+id/source_card')
test(source_card.attrib.get(ANDROID + 'layout_height') == '154dp', 'source cards match the compact two-column visual grid')
for view_id in ['source_image', 'title', 'source_latest', 'source_pin']:
    test(any(n.attrib.get(ANDROID + 'id', '').endswith('/' + view_id) for n in source_xml.iter()), f'source card retains {view_id} binding')

details_xml = ET.parse(ROOT / screens['manga_details']).getroot()
for view_id in ['manga_cover', 'title', 'manga_author', 'manga_status', 'manga_source', 'favorite_button', 'track_button', 'share_button', 'manga_summary']:
    test(any(n.attrib.get(ANDROID + 'id', '').endswith('/' + view_id) for n in details_xml.iter()), f'details preserve {view_id} binding')

reader_xml = ET.parse(ROOT / screens['reader']).getroot()
for view_id in ['viewer_container', 'page_number', 'toolbar', 'touch_view', 'reader_nav', 'chapters_sheet']:
    test(any(n.attrib.get(ANDROID + 'id', '').endswith('/' + view_id) for n in reader_xml.iter()), f'reader preserves {view_id} binding')

settings = read(screens['settings'])
test('PreferenceGroupAdapter' in settings, 'settings retain the original AndroidX Preference adapter')
test('RippleDrawable' in settings and 'MaterialSwitch' in settings, 'settings only restyle existing preference widgets')

# App identity is deliberately separate so V20 is distinguishable from the old install.
gradle = read('app/build.gradle.kts')
test('applicationIdSuffix = ".enhanced"' in gradle, 'existing dev application ID suffix is preserved')
test('versionNameSuffix = "-enhanced-v21-dev"' in gradle, 'V21 development build is identifiable')
test('versionCode = AndroidVersions.versionCode + 3' in gradle, 'V21 build code is higher than V20')
main_activity = read('app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt')
test('applyV20HomeStartPreference()' in main_activity, 'V21 retains the one-time Home landing preference')
test('preferences.startingTab().set(-4)' in main_activity, 'first V20 launch opens the redesigned Home screen')
test('home_start_preference_applied' in main_activity, 'the Home landing change is not repeated after user customization')
test('MANGA VIRUS V21' in read('app/src/dev/res/values/strings.xml'), 'English launcher label identifies V21')
test('فيروس مانجا V21' in read('app/src/dev/res/values-ar/strings.xml'), 'Arabic launcher label identifies V21')
workflow = read('.github/workflows/quality_gate.yml')
test('qa/v21/validate_visual_alignment.py' in workflow, 'GitHub Actions runs the V21 ten-screen source gate')
test("'feature/**'" in workflow, 'feature branch pushes automatically trigger CI')
test('secrets.JH_UPDATE_KEYSTORE_BASE64' in workflow, 'CI restores the existing V19 keystore from a GitHub secret')
for secret in ['JH_UPDATE_STORE_PASSWORD', 'JH_UPDATE_KEY_ALIAS', 'JH_UPDATE_KEY_PASSWORD']:
    test(f'secrets.{secret}' in workflow, f'CI receives signing secret {secret}')
test(workflow.count('Restore the persistent V19-compatible signing key') == 2, 'both ARM64 and universal jobs use the same persistent signing identity')
test('Missing required GitHub Actions secret' in workflow, 'CI refuses to produce an update APK without the persistent signing key')

# Parse every XML resource in the active dev flavor and ensure no duplicated IDs per layout.
xml_count = 0
for xml_path in (ROOT / 'app/src/dev/res').rglob('*.xml'):
    root = ET.parse(xml_path).getroot()
    xml_count += 1
    ids = [node.attrib.get(ANDROID + 'id') for node in root.iter() if node.attrib.get(ANDROID + 'id')]
    test(len(ids) == len(set(ids)), f'no duplicate view IDs in {xml_path.relative_to(ROOT)}')

test(xml_count >= 120, 'all dev XML resources were parsed')
print(f'V21 LEGACY VISUAL ALIGNMENT CHECKS: {checks}/{checks} PASS — source checks only; no APK or device validation.')
