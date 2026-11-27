from pathlib import Path

root = Path(__file__).resolve().parents[2]
p = root / 'app/src/main/java/eu/kanade/tachiyomi'
read = lambda f: (p / f).read_text()
down = read('data/download/Downloader.kt')
queue = read('data/download/model/DownloadQueue.kt')
reader = read('ui/reader/loader/HttpPageLoader.kt')
center = read('ui/download/center/DownloadCenterActivity.kt')
panel = read('ui/download/NeonDownloadPanel.kt')
checks = {
    'cancellation propagated when resolving image URLs': 'if (e is CancellationException) throw e\n                    page.status = Page.State.ERROR' in down,
    'download loop checks cancellation before read and write': 'currentCoroutineContext().ensureActive()\n                            val read' in down and 'currentCoroutineContext().ensureActive()\n                            output.write' in down,
    'response resource scoped to temporary file': 'val file = tmpDir.createFile("$filename.tmp")\n            try {\n                download.source.getImage(page).use' in down,
    'cancelled transfers not retried': 'if (cause is CancellationException) throw cause' in down,
    'concurrency matches existing preference ranges': 'parallelCount.coerceIn(1, 10)' in down and 'numberOfConcurrentPageDownloads().get().coerceIn(1, 15)' in down,
    'one set of observers per active download': 'download !in pageObservers' in queue and 'pageObservers[download] = pages.map' in queue,
    'observer release on removal, clear and nonactive status': 'stopPageObservers(download)' in queue and 'stopAllPageObservers()\n        store.clear()' in queue and 'pageObservers.remove(download)?.forEach { it.cancel() }' in queue and '} else {\n                stopPageObservers(download)' in queue,
    'listener callbacks use thread-safe collection': 'downloadListeners = CopyOnWriteArrayList' in queue and 'downloadListeners.addIfAbsent(listener)' in queue,
    'visible speculative page is kept and promoted': 'page.index != index && outsideReadAheadWindow' in reader and 'offerPage(visible, queuedByPage.getValue(visible).basePriority)' in reader and 'if (page.index == focusedPage) 3 else basePriority' in reader,
    'old focus priority is demoted': 'it.priority == 3 && it.page.index != index' in reader and 'PriorityPage(old.page, old.basePriority, old.basePriority)' in reader,
    'in-flight requests cannot be enqueued again': 'loadingPage === page) return@synchronized null' in reader and 'if (valid) loadingPage = request.page' in reader and 'if (loadingPage === page) loadingPage = null' in reader,
    'empty cached page list triggers source fetch': 'takeIf { it.isNotEmpty() }' in reader and '} ?: source.getPageList(chapter.chapter)' in reader,
    'single queue snapshot and one cache count per manga': 'val snapshot = manager.queue.toList()' in center and 'snapshot.distinctBy { it.manga.id }' in center and '_rows.value = snapshot.mapNotNull' in center,
    'refresh rate bounded with conflation': 'Channel.CONFLATED' in center and 'delay(125)' in center,
    'retry requires confirmation and skips manually paused chapters': 'if (showRetryFailed)' in panel and 'it.download.status == Download.State.ERROR && !it.paused' in panel and '.forEach { onResume(it.download) }' in panel,
}
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(checks.values()):
    raise SystemExit(1)
print(f'{len(checks)} static checks passed; no Android runtime claim.')
