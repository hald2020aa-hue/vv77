package eu.kanade.tachiyomi.ui.library.insights

import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.autoScrollDpPerSecond
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UltimateReaderLibraryTest {
    private fun entry(id: Long, source: Long, title: String, read: Int, unread: Int, vararg genres: String) =
        LibraryInsightEntry(id, source, title, unread, read, false, genres.toList(), "Source $source")

    @Test fun recommendationUsesReadGenresOnly() {
        val entries = listOf(entry(1, 1, "Read", 12, 0, "Action", "Fantasy"),
            entry(2, 2, "A", 0, 2, "action"), entry(3, 3, "B", 0, 8, "Fantasy", "Action"),
            entry(4, 1, "C", 0, 4, "Comedy"), entry(5, 2, "D", 1, 5, "Action"))
        assertEquals(listOf(3L, 2L), suggestUnreadTitles(entries).map { it.id })
        assertTrue(suggestUnreadTitles(emptyList()).isEmpty())
        assertTrue(suggestUnreadTitles(listOf(entry(6, 1, "Unseen", 0, 5, "Action"))).isEmpty())
    }

    @Test fun alternativesAreConservativeAndNeverMerge() {
        val records = listOf(entry(1, 1, "Same Title", 2, 3), entry(2, 2, "Same-title", 0, 6),
            entry(3, 1, "Unique", 1, 0), entry(4, 1, "Unique", 0, 1))
        val result = calculateLibraryInsights(records + records.first())
        assertEquals(4, result.titles)
        assertEquals(setOf(1L, 2L), result.alternatives.map { it.id }.toSet())
        assertEquals(2, result.likelyDuplicateTitles)
    }

    @Test fun scrollingSpeedsAreBounded() {
        assertEquals(48, autoScrollDpPerSecond(0))
        assertEquals(88, autoScrollDpPerSecond(1))
        assertEquals(144, autoScrollDpPerSecond(2))
        assertEquals(88, autoScrollDpPerSecond(Int.MIN_VALUE))
        assertEquals(88, autoScrollDpPerSecond(Int.MAX_VALUE))
    }
}
