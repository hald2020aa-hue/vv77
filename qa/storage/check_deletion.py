#!/usr/bin/env python3
"""Structural regression checks, not a claim of Android filesystem/queue runtime testing."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
base = root / 'app/src/main/java/eu/kanade/tachiyomi'
manager = (base / 'data/download/StorageManager.kt').read_text()
downloader = (base / 'data/download/Downloader.kt').read_text()
ui = (base / 'ui/home/StorageController.kt').read_text()
policy = (base / 'data/download/StorageDeletionPolicy.kt').read_text()
remove = manager.split('suspend fun deleteDownloads(', 1)[1].split('private data class ValidatedStorageFile', 1)[0]
checks = {
    'request bound to unique file URIs': 'request.targets.map { it.uri }.distinct().size' in remove and 'file.uri.toString() == target.uri' in remove,
    'real byte estimate from exact selection': 'targets.sumOf { it.bytes }' in policy,
    'read-only checked at preflight and immediately before deletion': 'check(!request.readOnly || chapter.read)' in remove and 'request.readOnly && !latest.read' in remove,
    'ambiguous manga and chapter paths rejected': 'Ambiguous manga download directory' in remove and 'Ambiguous chapter download' in remove and 'matches.single()' in remove,
    'direct filesystem links rejected before sizing and deleting': 'child.canonicalFile == File(expectedParent, child.name)' in manager and remove.index('validateTree(file, dir)') < remove.index('sizeOf(file)') and 'validateDirectChild(dir, sourceDir)' in remove,
    'only file paths deleted, never database records or parent folders': remove.count('file.delete()') == 1 and not any(token in remove for token in ['db.delete', 'db.insert', 'db.update', 'parentFile?.delete', 'deleteManga(', 'deleteChapters(']),
    'temporary names rejected and local source protected': 'name.endsWith(Downloader.TMP_DIR_SUFFIX' in manager and 'check(!manga.isLocal())' in remove,
    'scheduler start deferred while deleting': 'if (storageDeletionActive) {\n            deferredStart = true' in downloader,
    'workers joined before filesystem operation': 'pendingWorkerJobs().forEach { it.join() }\n                block()' in downloader and 'retiringJobs.add(job)' in downloader and 'launch(Dispatchers.IO)' in downloader,
    'queue restored before deletion and only targeted entries cancelled': 'restoreJob.join()' in downloader and 'queue.filter { it.manga.id in workIds }.forEach' in downloader and 'downloader.cancelStorageWorkDownloads(request.workIds)' in remove,
    'only works with selected files are cancelled': 'targets.map { it.mangaId }.toSet()' in policy,
    'queue restart after gate release': 'finishStorageDeletion(wasRunning) && queue.isNotEmpty()' in downloader,
    'verified deletions update cache; failure reported': 'file.delete() && !file.exists()' in remove and 'cache.removeChapters(listOf(chapter), manga)' in remove and 'StorageDeleteResult(deleted, failed)' in remove,
    'confirmation frozen; defaults to read-only; rejects empty action': 'deleteRequests = all to data.deletionRequest' in ui and 'deleteReadOnly = true' in ui and 'enabled = !deleting && request.targets.isNotEmpty()' in ui,
    'multiple selection, category and explicit confirmation': 'onLongClick = {' in ui and 'Checkbox(checked = workId in selectedWorkIds' in ui and 'prepareDeletion(deletable)' in ui and 'executeDeletion(request)' in ui,
    'errors visible and reload even after failure': 'deletionFailed = true' in ui and 'R.string.storage_delete_error' in ui and 'reload.update { it + 1 }' in ui,
}
for label, ok in checks.items():
    assert ok, label
    print('PASS:', label)
keys = []
for qualifier in ('values', 'values-ar'):
    xml = ET.parse(root / 'app/src/main/res' / qualifier / 'storage_inventory.xml').getroot()
    keys.append({x.get('name') for x in xml if x.get('name', '').startswith('storage_delete_')})
assert keys[0] == keys[1] and len(keys[0]) == 13
print('PASS: all 13 deletion labels exist in default and Arabic resources')
print('Static safeguards only; SAF, cancellation, deletion, reading history and Android UI require real device tests.')
