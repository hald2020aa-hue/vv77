package eu.kanade.tachiyomi.data.download

/** Aggregation over matched completed downloads only; never claims all filesystem usage. */
data class StorageBreakdownEntry(
    val type: StorageSeriesType,
    val bytes: Long,
    val chapters: Int,
    val readBytes: Long,
    val readChapters: Int,
)

data class StorageTypeUsage(val type: StorageSeriesType, val works: Int, val chapters: Int, val bytes: Long)

data class StorageBreakdown(val types: List<StorageTypeUsage>, val readBytes: Long, val readChapters: Int)

fun calculateStorageBreakdown(entries: List<StorageBreakdownEntry>): StorageBreakdown {
    val groups = entries.groupBy { it.type }.map { (type, works) ->
        StorageTypeUsage(type, works.size, works.sumOf { it.chapters }, works.sumOf { it.bytes })
    }.sortedByDescending { it.bytes }
    return StorageBreakdown(groups, entries.sumOf { it.readBytes }, entries.sumOf { it.readChapters })
}
