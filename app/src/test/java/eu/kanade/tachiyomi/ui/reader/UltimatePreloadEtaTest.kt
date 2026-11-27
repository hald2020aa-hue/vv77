package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.ui.enhanced.estimatedTransferSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UltimatePreloadEtaTest {
    @Test fun speculativePreloadRespectsSettingsAndBounds() {
        assertFalse(shouldPreloadAdjacent(20, 20, 0))
        assertFalse(shouldPreloadAdjacent(0, 20, 7))
        assertFalse(shouldPreloadAdjacent(21, 20, 7))
        assertFalse(shouldPreloadAdjacent(1, 0, 7))
        assertFalse(shouldPreloadAdjacent(15, 20, 7))
        assertTrue(shouldPreloadAdjacent(16, 20, 7))
        assertFalse(shouldPreloadAdjacent(19, 20, 1))
        assertTrue(shouldPreloadAdjacent(20, 20, 1))
        assertTrue(shouldPreloadAdjacent(1, 1, 7))
        assertFalse(shouldPreloadAdjacent(1, 20, Int.MAX_VALUE))
    }

    @Test fun transferEstimateNeverRoundsUnfinishedWorkToZero() {
        assertEquals(null, estimatedTransferSeconds(null, 1L, 2L))
        assertEquals(null, estimatedTransferSeconds(0L, 0L, 2L))
        assertEquals(null, estimatedTransferSeconds(10L, -1L, 2L))
        assertEquals(null, estimatedTransferSeconds(10L, 1L, 0L))
        assertEquals(1L, estimatedTransferSeconds(10L, 9L, 100L))
        assertEquals(2L, estimatedTransferSeconds(10L, 3L, 4L))
        assertEquals(0L, estimatedTransferSeconds(10L, 10L, 4L))
        assertEquals(Long.MAX_VALUE, estimatedTransferSeconds(Long.MAX_VALUE, 0L, 1L))
    }
}
