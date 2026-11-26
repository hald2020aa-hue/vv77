package eu.kanade.tachiyomi.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageSeriesTypeTest {
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
