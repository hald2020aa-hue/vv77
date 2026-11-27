package eu.kanade.tachiyomi.ui.enhanced

import eu.kanade.tachiyomi.data.download.StorageBreakdownEntry
import eu.kanade.tachiyomi.data.download.StorageSeriesType
import eu.kanade.tachiyomi.data.download.calculateStorageBreakdown
import eu.kanade.tachiyomi.ui.library.insights.LibraryInsightEntry
import eu.kanade.tachiyomi.ui.library.insights.calculateLibraryInsights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UltimateInsightsTest {
    @Test
    fun readingStatesAreDisjointAndIgnoreRepeatedCategories() {
        val a = LibraryInsightEntry(1, 1, "A", 2, 0, true)
        val b = LibraryInsightEntry(2, 1, "B", 1, 1, false)
        val c = LibraryInsightEntry(3, 1, "C", 0, 2, false)
        val d = LibraryInsightEntry(4, 1, "D", 0, 0, false)
        val result = calculateLibraryInsights(listOf(a, a, b, c, d))
        assertEquals(4, result.titles)
        assertEquals(1, result.notStartedTitles)
        assertEquals(1, result.inProgressTitles)
        assertEquals(1, result.caughtUpTitles)
        assertEquals(1, result.unknownChapterTitles)
        assertEquals(0.5f, result.readFraction!!, 0.001f)
        assertNull(calculateLibraryInsights(emptyList()).readFraction)
    }

    @Test
    fun storageGroupsUseMatchedBytesAndReadCounts() {
        val result = calculateStorageBreakdown(listOf(
            StorageBreakdownEntry(StorageSeriesType.MANGA, 50, 2, 10, 1),
            StorageBreakdownEntry(StorageSeriesType.MANGA, 100, 3, 20, 1),
            StorageBreakdownEntry(StorageSeriesType.MANHWA, 300, 4, 0, 0),
        ))
        assertEquals(2, result.types.size)
        assertEquals(StorageSeriesType.MANHWA, result.types.first().type)
        assertEquals(150L, result.types.last().bytes)
        assertEquals(2, result.readChapters)
        assertEquals(30L, result.readBytes)
    }
}
