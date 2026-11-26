package eu.kanade.tachiyomi.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageSeriesTypeTest {
    @Test
    fun manualTypeWins() {
        assertEquals(StorageSeriesType.MANHWA, StorageSeriesType.resolve("MANHWA", listOf("manga")))
        assertEquals(StorageSeriesType.UNCLASSIFIED, StorageSeriesType.resolve("UNCLASSIFIED", listOf("manhua")))
        assertEquals(StorageSeriesType.MANHUA, StorageSeriesType.resolve(null, listOf("manhua")))
        assertEquals(StorageSeriesType.MANGA, StorageSeriesType.resolve("unknown", listOf("manga")))
    }

    @Test
    fun exactTagsOnly() {
        assertEquals(StorageSeriesType.MANGA, StorageSeriesType.fromTags(listOf("action", " MANGA ")))
        assertEquals(StorageSeriesType.MANHWA, StorageSeriesType.fromTags(listOf("Manhwa", "MANHWA")))
        assertEquals(StorageSeriesType.MANHUA, StorageSeriesType.fromTags(listOf("manhua")))
        assertEquals(StorageSeriesType.UNCLASSIFIED, StorageSeriesType.fromTags(null))
        assertEquals(StorageSeriesType.UNCLASSIFIED, StorageSeriesType.fromTags(listOf("korean", "webtoon")))
        assertEquals(StorageSeriesType.UNCLASSIFIED, StorageSeriesType.fromTags(listOf("manga", "manhua")))
    }
}
