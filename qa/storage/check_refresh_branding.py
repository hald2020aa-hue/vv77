#!/usr/bin/env python3
"""Static regression checks for the dev display label and storage-only additive refresh."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
main = root / 'app/src/main/res'
dev = root / 'app/src/dev/res'
labels = {'app_name', 'app_short_name'}

def get_labels(folder):
    return {e.get('name'): e.text for path in folder.glob('*.xml') for e in ET.parse(path).getroot()
            if e.tag == 'string' and e.get('name') in labels}

expected = dict.fromkeys(labels, 'فيروس مانجا')
assert get_labels(dev / 'values') == expected
for path in main.glob('values-*'):
    if get_labels(path):
        assert get_labels(dev / path.name) == expected, path.name
for path in dev.glob('values-*'):
    if get_labels(path):
        assert get_labels(path) == expected, path.name
print('PASS: exact Arabic dev app and launcher label across all existing label qualifiers')

base = root / 'app/src/main/java/eu/kanade/tachiyomi'
manager = (base/'data/download/StorageManager.kt').read_text()
controller = (base/'ui/home/StorageController.kt').read_text()
sync = (base/'util/chapter/ChapterSourceSync.kt').read_text()
checks = {
    'normal callers retain old default': 'preserveExisting: Boolean = false' in sync,
    'existing metadata updates gated': 'else if (!preserveExisting)' in sync,
    'missing chapters cannot be deleted in storage mode': 'if (preserveExisting) emptyList() else dbChapters.filterNot' in sync,
    'source-order rewriting gated': 'if (!preserveExisting) {\n            db.fixChaptersSourceOrder' in sync,
    'new chapter result is real insertion list': 'if (preserveExisting) return Pair(toAdd.toList(), emptyList())' in sync,
    'storage invokes existing fetch and sync': 'source.getMangaUpdate(' in manager and 'preserveExisting = true' in manager,
    'duplicate storage refresh guard and release': 'check(activeRefreshes.add(id))' in manager and 'finally {\n            activeRefreshes.remove(id)' in manager,
    'only work opening and explicit retry initiate refresh': controller.count('refreshWork()') == 4 and 'selectedMangaId = id\n        refreshWork()' in controller,
    'offline bypass before launching': controller.index('view?.context?.isOnline() != true') < controller.index('refreshJob = viewScope.launch'),
    'errors visible and retry': 'refreshState = RefreshState.ERROR' in controller and 'R.string.storage_chapters_update_error' in controller and 'R.string.retry' in controller,
    'closing a work cancels the request': 'refreshJob?.cancel()' in controller,
}
for label, ok in checks.items():
    assert ok, label
    print('PASS:', label)
print('Static checks only; no Android runtime or live source test.')
