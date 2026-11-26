#!/usr/bin/env python3
"""Structural checks for cover request reuse; not an Android runtime test."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
base = ROOT / 'app/src/main/java/eu/kanade/tachiyomi'


def check(ok, label):
    assert ok, label
    print('PASS:', label)


view = (base / 'data/image/coil/MangaCoverImageView.kt').read_text()
key = (base / 'data/image/coil/CoverBindingKey.kt').read_text()
loader = (base / 'data/image/coil/LibraryMangaImageTarget.kt').read_text()
check('data class CoverBindingKey' in key and all(f'val {field}:' in key for field in ('mangaId', 'sourceId', 'url', 'thumbnailUrl', 'favorite', 'lastUpdate', 'widthPx', 'heightPx')), 'immutable request identity includes cover and size')
check('if (request == lastRequest) return' in view and view.index('if (request == lastRequest) return') < view.index('loadManga(manga)'), 'unchanged image requests are skipped')
check('manga.id, manga.source, manga.url, manga.thumbnail_url, manga.favorite,' in view and 'manga.last_update, measuredWidth, measuredHeight,' in view, 'request identity is built from actual manga')
check('!isAttachedToWindow || measuredWidth <= 0 || measuredHeight <= 0' in view, 'wait for attached nonzero measured size')
check('loadManga(manga) { size(measuredWidth, measuredHeight) }' in view and '.data(manga)' in loader and 'LibraryMangaImageTarget(this, manga)' in loader, 'measured dimensions and original authenticated cover pipeline')
check('override fun onSizeChanged' in view and 'requestCover(w, h)' in view, 'resize requests use new pixel dimensions')
check('override fun onDetachedFromWindow()' in view and 'dispose()\n        lastRequest = null' in view and 'override fun onAttachedToWindow()' in view, 'attachment lifecycle releases and rebinds')
check('fun releaseCover()' in view and 'boundManga = null' in view and 'setImageDrawable(null)' in view, 'released views drop the work and drawable')
for path, call in {
    'ui/home/HomeController.kt': 'it.bindCover(manga)',
    'ui/home/StorageController.kt': 'it.bindCover(work.manga)',
    'ui/download/NeonDownloadPanel.kt': 'it.bindCover(row.download.manga)',
    'ui/download/center/DownloadCenterActivity.kt': 'it.bindCover(entry.cover, entry.title)',
}.items():
    s = (base / path).read_text()
    check('MangaCoverImageView(' in s and call in s and 'onRelease = { it.releaseCover() }' in s and 'loadManga(' not in s, f'{path}: stable cover binding and explicit release')
print('Cover binding checks are static; actual requests and memory use must be measured on Android.')
