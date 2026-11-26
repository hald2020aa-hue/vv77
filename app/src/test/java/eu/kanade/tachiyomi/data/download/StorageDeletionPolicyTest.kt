package eu.kanade.tachiyomi.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageDeletionPolicyTest {
    private fun candidate(work: Long, chapter: Long, bytes: Long, read: Boolean) =
        StorageDeleteCandidate(StorageDeleteTarget(work, chapter, bytes, 123L, "file:///test/$work/$chapter"), read)

    private val data = listOf(candidate(1, 11, 10, true), candidate(1, 12, 20, false), candidate(2, 21, 40, false), candidate(3, 31, 80, true))

    @Test
    fun readOnlyExcludesUnreadAndUnselectedWorks() {
        val result = StorageDeletionPolicy.request(setOf(1L, 2L), true, data)
        assertEquals(listOf(11L), result.targets.map { it.chapterId })
        assertEquals(setOf(1L), result.workIds)
        assertEquals(10L, result.bytes)
    }

    @Test
    fun allIncludesOnlySelectedWorks() {
        val result = StorageDeletionPolicy.request(setOf(1L, 2L), false, data)
        assertEquals(listOf(11L, 12L, 21L), result.targets.map { it.chapterId })
        assertEquals(70L, result.bytes)
        assertEquals(setOf(1L, 2L), result.workIds)
    }

    @Test
    fun emptySelectionOrNoReadChaptersCancelsNothing() {
        val result = StorageDeletionPolicy.request(setOf(2L), true, data)
        assertTrue(result.targets.isEmpty())
        assertTrue(result.workIds.isEmpty())
        assertEquals(0L, result.bytes)
        assertTrue(StorageDeletionPolicy.request(emptySet(), false, data).targets.isEmpty())
    }
}
