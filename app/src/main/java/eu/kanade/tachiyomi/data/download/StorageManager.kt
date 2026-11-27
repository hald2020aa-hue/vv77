package eu.kanade.tachiyomi.data.download

import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.isLocal
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy

data class StoredChapter(val chapter: Chapter, val bytes: Long, val modifiedAt: Long, val fileUri: String)

data class StorageDeleteResult(val deletedCount: Int, val failedCount: Int)

data class StoredManga(
    val manga: Manga,
    val chapters: List<StoredChapter>,
    val categories: List<Category>,
    val type: StorageSeriesType,
    val lastReadAt: Long,
) {
    val bytes: Long = chapters.sumOf { it.bytes }
    val oldestFileAt: Long? = chapters.asSequence().map { it.modifiedAt }.filter { it > 0L }.minOrNull()
}

data class StorageSnapshot(val works: List<StoredManga>) {
    val bytes: Long = works.sumOf { it.bytes }
    val chapterCount: Int = works.sumOf { it.chapters.size }
    // Snapshot-only aggregates: no extra scan, network request or deletion.
    val breakdown = calculateStorageBreakdown(works.map { work ->
        StorageBreakdownEntry(
            type = work.type,
            bytes = work.bytes,
            chapters = work.chapters.size,
            readBytes = work.chapters.filter { it.chapter.read }.sumOf { it.bytes },
            readChapters = work.chapters.count { it.chapter.read },
        )
    })

    fun deletionRequest(workIds: Set<Long>, readOnly: Boolean): StorageDeleteRequest {
        val candidates = works.filter { !it.manga.isLocal() && it.manga.id in workIds }.flatMap { work ->
            val mangaId = checkNotNull(work.manga.id)
            work.chapters.mapNotNull { entry ->
                entry.chapter.id?.let {
                    StorageDeleteCandidate(StorageDeleteTarget(mangaId, it, entry.bytes, entry.modifiedAt, entry.fileUri), entry.chapter.read)
                }
            }
        }
        return StorageDeletionPolicy.request(workIds, readOnly, candidates)
    }
}

/** Inventory and explicit deletion of matched, completed downloads. Classification alone writes manga metadata. */
class StorageManager internal constructor(
    private val provider: DownloadProvider,
    private val cache: DownloadCache,
    private val sourceManager: SourceManager,
    private val downloader: Downloader,
) {
    private val db by injectLazy<DatabaseHelper>()
    private val mutex = Mutex()
    private var saved: Pair<Long, StorageSnapshot>? = null
    val changes = cache.storageRevision
    private val activeRefreshes = ConcurrentHashMap.newKeySet<Long>()

    /** Only called from storage. Existing chapter metadata and download paths are kept intact. */
    suspend fun refreshChapters(id: Long): List<Chapter> = withContext(Dispatchers.IO) {
        check(activeRefreshes.add(id)) { "Storage chapter refresh already running" }
        try {
            val manga = checkNotNull(db.getManga(id).executeAsBlocking())
            if (manga.isLocal()) return@withContext emptyList()
            val source = checkNotNull(sourceManager.get(manga.source))
            val update = source.getMangaUpdate(manga, emptyList(), fetchDetails = false, fetchChapters = true)
            currentCoroutineContext().ensureActive()
            mutex.withLock {
                val (added, _) = syncChaptersWithSource(db, update.chapters, manga, source, preserveExisting = true)
                saved = null
                added
            }
        } finally {
            activeRefreshes.remove(id)
        }
    }

    /** Refresh on demand to include external filesystem changes and fresh DB metadata. */
    suspend fun snapshot(force: Boolean = false): StorageSnapshot = withContext(Dispatchers.IO) {
        cache.awaitInitialScan()
        mutex.withLock {
            val revision = changes.value
            if (!force) saved?.takeIf { it.first == revision }?.let { return@withLock it.second }
            val result = scan()
            if (changes.value == revision) saved = revision to result
            result
        }
    }

    suspend fun setManualType(id: Long, type: StorageSeriesType?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(db.setStorageManualType(id, type) == 1) { "Manga no longer exists" }
            saved = null
        }
    }

    /** A confirmation is bound to exact files. Recheck every target before cancelling the queue. */
    suspend fun deleteDownloads(request: StorageDeleteRequest): StorageDeleteResult =
        withContext(NonCancellable + Dispatchers.IO) {
            require(request.targets.isNotEmpty())
            require(request.targets.map { it.uri }.distinct().size == request.targets.size)
            downloader.withStorageDeletion {
                mutex.withLock {
                    val files = withContext(Dispatchers.IO) {
                        val allManga = db.getMangas().executeAsBlocking()
                        request.targets.groupBy { it.mangaId }.flatMap { (id, targets) ->
                            check(id in request.workIds && id !in activeRefreshes)
                            val manga = checkNotNull(db.getManga(id).executeAsBlocking())
                            check(!manga.isLocal())
                            val source = checkNotNull(sourceManager.get(manga.source))
                            val sourceDir = checkNotNull(provider.findSourceDir(source))
                            val dir = checkNotNull(provider.findMangaDir(manga, source))
                            if (sourceDir.uri.scheme == "file") validateDirectChild(sourceDir, checkNotNull(sourceDir.parentFile))
                            validateDirectChild(dir, sourceDir)
                            // Sanitized titles can collide, including across sources with identical folder names.
                            check(allManga.count { other ->
                                val otherSource = sourceManager.get(other.source)
                                otherSource != null && provider.getMangaDirName(other).equals(provider.getMangaDirName(manga), true) &&
                                    provider.getSourceDirName(otherSource).equals(provider.getSourceDirName(source), true)
                            } == 1) { "Ambiguous manga download directory" }
                            val chapters = db.getChapters(manga).executeAsBlocking()
                            val children = checkNotNull(dir.listFiles())
                            targets.map { target ->
                                val chapter = chapters.single { it.id == target.chapterId }
                                check(!request.readOnly || chapter.read) { "Read status changed" }
                                val matches = children.filter { child -> matchesChapter(child, chapter) }
                                val file = matches.single()
                                validateTree(file, dir)
                                check(file.uri.toString() == target.uri && file.lastModified() == target.modifiedAt && sizeOf(file) == target.bytes) {
                                    "Download changed since confirmation"
                                }
                                check(chapters.count { matchesChapter(file, it) } == 1) { "Ambiguous chapter download" }
                                ValidatedStorageFile(manga, chapter, dir, file, target)
                            }
                        }
                    }
                    // Cancel only the selected works; all other queue entries survive the pause.
                    downloader.cancelStorageWorkDownloads(request.workIds)
                    withContext(Dispatchers.IO) {
                        var deleted = 0
                        var failed = 0
                        try {
                            for ((manga, chapter, dir, file, target) in files) {
                                val latest = db.getChapter(target.chapterId).executeAsBlocking()
                                if (latest == null || latest.manga_id != target.mangaId ||
                                    request.readOnly && !latest.read || !file.exists() || !matchesChapter(file, latest)
                                ) {
                                    failed++
                                    continue
                                }
                                validateTree(file, dir)
                                if (file.lastModified() != target.modifiedAt || sizeOf(file) != target.bytes) {
                                    failed++
                                    continue
                                }
                                // Never delete the parent manga/source folder or temporary downloads.
                                if (file.delete() && !file.exists()) {
                                    cache.removeChapters(listOf(chapter), manga)
                                    deleted++
                                } else {
                                    failed++
                                }
                            }
                            StorageDeleteResult(deleted, failed)
                        } finally {
                            saved = null
                            cache.forceRenewCache()
                        }
                    }
                }
            }
        }

    private data class ValidatedStorageFile(
        val manga: Manga,
        val chapter: Chapter,
        val parent: UniFile,
        val file: UniFile,
        val target: StorageDeleteTarget,
    )

    private fun matchesChapter(file: UniFile, chapter: Chapter): Boolean {
        val name = file.name ?: return false
        if (name.endsWith(Downloader.TMP_DIR_SUFFIX, ignoreCase = true)) return false
        return provider.getValidChapterDirNames(chapter).any { candidate ->
            (file.isDirectory && name.equals(candidate, true)) ||
                (file.isFile && name.equals("$candidate.cbz", true))
        }
    }

    /** Reject symlinks and paths that are not a direct child of the configured directory. */
    private fun validateDirectChild(file: UniFile, parent: UniFile) {
        if (file.uri.scheme == "file") {
            val child = File(checkNotNull(file.uri.path))
            val expectedParent = File(checkNotNull(parent.uri.path)).canonicalFile
            check(child.canonicalFile == File(expectedParent, child.name)) { "Unsafe download path" }
        }
    }

    private fun validateTree(file: UniFile, parent: UniFile) {
        validateDirectChild(file, parent)
        if (file.isDirectory) checkNotNull(file.listFiles()).forEach { validateTree(it, file) }
    }

    private suspend fun scan(): StorageSnapshot {
        val seenMangaDirs = mutableSetOf<Uri>()
        val seenChapterDirs = mutableSetOf<Uri>()
        val works = mutableListOf<StoredManga>()
        // Prefer a library record if multiple records refer to the same directory.
        val mangas = db.getMangas().executeAsBlocking().sortedWith(
            compareByDescending<Manga> { it.favorite }.thenBy { it.id },
        )
        for (manga in mangas) {
            currentCoroutineContext().ensureActive()
            val source = sourceManager.get(manga.source) ?: continue
            val directory = provider.findMangaDir(manga, source) ?: continue
            if (!seenMangaDirs.add(directory.uri)) continue
            val files = directory.listFiles() ?: error("Cannot list download directory: ${manga.id}")
            val byName = files.filter { it.isDirectory || it.isFile && it.name.orEmpty().endsWith(".cbz", true) }
                .groupBy { it.name.orEmpty().lowercase() }
            val chapters = mutableListOf<StoredChapter>()
            for (chapter in db.getChapters(manga).executeAsBlocking()) {
                currentCoroutineContext().ensureActive()
                val file = provider.getValidChapterDirNames(chapter).asSequence().flatMap {
                    sequenceOf(it.lowercase(), "${it.lowercase()}.cbz")
                }.mapNotNull { byName[it]?.singleOrNull() }.firstOrNull() ?: continue
                if (!seenChapterDirs.add(file.uri)) continue
                chapters += StoredChapter(chapter, sizeOf(file), file.lastModified(), file.uri.toString())
            }
            if (chapters.isNotEmpty()) {
                works += StoredManga(
                    manga, chapters, db.getCategoriesForManga(manga).executeAsBlocking(),
                    StorageSeriesType.resolve(manga.storageManualType, manga.originalGenre?.split(",")),
                    manga.id?.let { db.getHistoryByMangaId(it).executeAsBlocking().maxOfOrNull { history -> history.last_read } } ?: 0L,
                )
            }
        }
        return StorageSnapshot(works.sortedByDescending { it.bytes })
    }

    private suspend fun sizeOf(file: UniFile): Long {
        currentCoroutineContext().ensureActive()
        if (file.isFile) return file.length().also { check(it >= 0L) }
        val children = file.listFiles() ?: error("Cannot list downloaded chapter")
        var total = 0L
        for (child in children) total += sizeOf(child)
        return total
    }
}
