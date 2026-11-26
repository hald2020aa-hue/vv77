#!/usr/bin/env python3
"""Static regression guard for Storage root navigation; not Android UI/device QA."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
A = '{http://schemas.android.com/apk/res/android}'
APP = '{http://schemas.android.com/apk/res-auto}'


def require(ok, label):
    assert ok, label
    print('PASS:', label)


expected = {
    ('main', 'bottom'): ['nav_home', 'nav_library', 'nav_recents', 'nav_browse', 'nav_storage'],
    ('dev', 'bottom'): ['nav_home', 'nav_library', 'nav_browse', 'nav_recents', 'nav_storage', 'nav_settings'],
    ('main', 'side'): ['nav_home', 'nav_library', 'nav_recents', 'nav_recents_group', 'nav_summary', 'nav_ungrouped', 'nav_history', 'nav_updates', 'nav_browse', 'nav_storage', 'nav_settings'],
    ('dev', 'side'): ['nav_home', 'nav_library', 'nav_recents', 'nav_recents_group', 'nav_summary', 'nav_ungrouped', 'nav_history', 'nav_updates', 'nav_browse', 'nav_storage', 'nav_settings'],
}
for (flavor, kind), ids in expected.items():
    path = ROOT / f'app/src/{flavor}/res/menu/{kind}_navigation.xml'
    menu = ET.parse(path).getroot()
    items = list(menu.iter('item'))
    actual = [i.get(A + 'id', '').split('/')[-1] for i in items]
    require(actual == ids, f'{flavor} {kind}: all original destinations and their order preserved')
    storage = next(i for i in items if i.get(A + 'id') == '@+id/nav_storage')
    require(storage.get(A + 'title') == '@string/storage_inventory_title' and storage.get(A + 'icon') == '@drawable/ic_storage_outline_24dp', f'{flavor} {kind}: storage label and icon')
    if kind == 'bottom':
        # Material Components 1.14.0 BottomNavigationView supports up to six items.
        require(len(items) <= 6, f'{flavor} bottom: at most six items')
        layout = ET.parse(ROOT / f'app/src/{flavor}/res/layout/main_activity.xml').getroot()
        nav = next(v for v in layout.iter() if v.get(A + 'id') == '@+id/bottom_nav')
        require(nav.tag.endswith('.BottomNavigationView') and nav.get(APP + 'itemIconGravity') == 'top' and nav.get(APP + 'itemHorizontalTranslationEnabled') == 'false' and nav.get(APP + 'labelMaxLines') == '2', f'{flavor}: compact fixed-width navigation labels')

base = ROOT / 'app/src/main/java/eu/kanade/tachiyomi'
main = (base / 'ui/main/MainActivity.kt').read_text()
storage = (base / 'ui/home/StorageController.kt').read_text()
require(main.count('is StorageController -> R.id.nav_storage') == 2 and 'R.id.nav_storage -> StorageController()' in main, 'storage destination and both selection restoration paths')
require('tabId != R.id.nav_storage' in main, 'storage does not overwrite previous starting-tab preference')
require('controller is StorageController && controller === router.backstack.firstOrNull()?.controller' in main, 'rail root storage differs from settings-pushed storage')
require('override fun canChangeTabs(block: () -> Unit): Boolean = !deleting && !savingType' in storage and 'currentController is StorageController && !currentController.canChangeTabs {}' in main, 'tab changes blocked during deletion or classification write')
require('LaunchedEffect(canStillGoBack())' in storage and 'reEnableBackPressedCallBack()' in storage, 'root back callback follows storage sub-screen state')
require('bottomNav?.takeIf { it.isVisible }?.height' in storage and 'cachedSystemInsets?.bottom' in storage, 'content clears visible bottom bar or system inset')
require('navigateTo(StorageController())' in (base / 'ui/setting/SettingsMainController.kt').read_text(), 'settings entry remains available')
require('material:1.14.0' in (ROOT / 'app/build.gradle.kts').read_text(), 'six-destination dependency version unchanged')
for q, expected_label in [('values', 'Storage'), ('values-ar', 'التخزين')]:
    root = ET.parse(ROOT / f'app/src/main/res/{q}/storage_inventory.xml').getroot()
    require(any(i.get('name') == 'storage_inventory_title' and i.text == expected_label for i in root), f'{q} localized storage title')
ET.parse(ROOT / 'app/src/main/res/drawable/ic_storage_outline_24dp.xml')
print('PASS: storage vector XML parses')
print('Storage navigation checks are static; actual touch targets, layout, state restore and foldable/RTL behavior require device tests.')
