package eu.kanade.tachiyomi.data.image.coil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CoverBindingKeyTest {
    private val key = CoverBindingKey(1L, 10L, "/manga", "https://example.invalid/cover", true, 0L, 216, 312)

    @Test
    fun sameCoverAndSizeShareIdentity() {
        assertEquals(key, key.copy())
    }

    @Test
    fun changedCoverOrWorkRequiresNewRequest() {
        assertNotEquals(key, key.copy(mangaId = 2L))
        assertNotEquals(key, key.copy(sourceId = 11L))
        assertNotEquals(key, key.copy(url = "/another"))
        assertNotEquals(key, key.copy(thumbnailUrl = null))
        assertNotEquals(key, key.copy(favorite = false))
        assertNotEquals(key, key.copy(lastUpdate = 1L))
    }

    @Test
    fun resizingForFoldableRequiresNewRequest() {
        assertNotEquals(key, key.copy(widthPx = 432))
        assertNotEquals(key, key.copy(heightPx = 624))
    }
}
