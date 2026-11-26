package eu.kanade.tachiyomi.data.download

import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy

data class StoredChapter(val chapter: Chapter, val bytes: Long, val modifiedAt: Long)

data class StoredManga(
    val manga: Manga,
    val chapters: List<StoredChapter>,
    val categories: List<Category>,
    val type: StorageSeriesType,
    val lastReadAt: Long,
) {
    val bytes: Long get() = chapters.sumOf { it.bytes }
    val oldestFileAt: Long? get() = chapters.map { it.modifiedAt }.filter { it > 0L }.minOrNull()
}

data class StorageSnapshot(val works: List<StoredManga>) {
    val bytes: Long get() = works.sumOf { it.bytes }
    val chapterCount: Int get() = works.sumOf { it.chapters.size }
}

/** Inventory of matched, completed downloads; only explicit classification writes metadata. */
class StorageManager internal constructor(
    private val provider: DownloadProvider,
    private val cache: DownloadCache,
    private val sourceManager: SourceManager,
) {
    private val db by injectLazy<DatabaseHelper>()
    private val mutex = Mutex()
    private var saved: Pair<Long, StorageSnapshot>? = null
    val changes = cache.storageRevision

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
                chapters += StoredChapter(chapter, sizeOf(file), file.lastModified())
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
