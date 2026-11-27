package eu.kanade.tachiyomi.qa.core

import eu.kanade.tachiyomi.ui.library.insights.LibraryInsightEntry
import eu.kanade.tachiyomi.ui.library.insights.calculateLibraryInsights
import eu.kanade.tachiyomi.data.download.StorageSeriesType
import eu.kanade.tachiyomi.data.download.StorageBreakdownEntry
import eu.kanade.tachiyomi.data.download.calculateStorageBreakdown

fun main() {
    var count = 0
    fun verify(label: String, condition: Boolean) {
        check(condition) { label }
        count++
        println("PASS: $label")
    }
    val records = listOf(
        LibraryInsightEntry(1, 100, "Title A", 3, 0, false),
        LibraryInsightEntry(2, 200, "Title B", 2, 3, false),
        LibraryInsightEntry(3, 200, "Title C", 0, 5, false),
        LibraryInsightEntry(4, 200, "Title D", 0, 0, true),
        LibraryInsightEntry(5, 200, "Title E", -1, 4, false),
        LibraryInsightEntry(6, 200, "Title F", 3, -1, false),
    )
    val r = calculateLibraryInsights(records + records.first())
    verify("deduplicate library records", r.titles == 6)
    verify("not started", r.notStartedTitles == 1)
    verify("reading in progress", r.inProgressTitles == 1)
    verify("caught up", r.caughtUpTitles == 1)
    verify("unknown counts", r.unknownChapterTitles == 3)
    verify("all categories account for titles", r.notStartedTitles + r.inProgressTitles + r.caughtUpTitles + r.unknownChapterTitles == r.titles)
    verify("publication complete independent of read state", r.completedTitles == 1)
    verify("fraction uses actual nonnegative chapter counts", r.readFraction == 12f / 20f)
    verify("no fake progress for empty library", calculateLibraryInsights(emptyList()).readFraction == null)
    val huge = calculateLibraryInsights(records.take(2).map { it.copy(read = Int.MAX_VALUE, unread = Int.MAX_VALUE) })
    verify("large counts do not overflow", huge.readChapters == Int.MAX_VALUE && huge.readFraction == 0.5f)
    val b = calculateStorageBreakdown(listOf(
        StorageBreakdownEntry(StorageSeriesType.MANGA, 100, 4, 50, 2),
        StorageBreakdownEntry(StorageSeriesType.MANGA, 200, 5, 0, 0),
        StorageBreakdownEntry(StorageSeriesType.MANHWA, 400, 3, 400, 3),
        StorageBreakdownEntry(StorageSeriesType.UNCLASSIFIED, 0, 1, 0, 1),
    ))
    verify("group only existing types", b.types.size == 3)
    verify("sort by usage", b.types.first().type == StorageSeriesType.MANHWA)
    verify("aggregate same type", b.types[1].works == 2 && b.types[1].chapters == 9 && b.types[1].bytes == 300L)
    verify("total matched bytes", b.types.sumOf { it.bytes } == 700L)
    verify("read estimate", b.readBytes == 450L && b.readChapters == 6)
    verify("empty storage", calculateStorageBreakdown(emptyList()).types.isEmpty() && calculateStorageBreakdown(emptyList()).readBytes == 0L)
    println("ULTIMATE 02: $count checks passed")
}
