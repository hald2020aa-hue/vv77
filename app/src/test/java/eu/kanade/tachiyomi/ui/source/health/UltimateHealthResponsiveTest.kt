package eu.kanade.tachiyomi.ui.source.health

import eu.kanade.tachiyomi.ui.enhanced.neonColumnCount
import eu.kanade.tachiyomi.ui.enhanced.neonContentWidthPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UltimateHealthResponsiveTest {
    @Test fun filtersSeparateLimitedFailedAndUntested() {
        assertTrue(SourceHealthFilter.ALL.matches(ProbeStatus.CHECKING))
        assertTrue(SourceHealthFilter.AVAILABLE.matches(ProbeStatus.AVAILABLE))
        assertFalse(SourceHealthFilter.AVAILABLE.matches(ProbeStatus.LIMITED))
        assertTrue(SourceHealthFilter.LIMITED.matches(ProbeStatus.LIMITED))
        assertTrue(SourceHealthFilter.UNAVAILABLE.matches(ProbeStatus.INVALID_ENDPOINT))
        assertTrue(SourceHealthFilter.UNAVAILABLE.matches(ProbeStatus.UNAVAILABLE))
        assertTrue(SourceHealthFilter.UNTESTED.matches(ProbeStatus.NOT_CHECKED))
        assertTrue(SourceHealthFilter.UNTESTED.matches(ProbeStatus.CHECKING))
        assertFalse(SourceHealthFilter.UNTESTED.matches(ProbeStatus.UNAVAILABLE))
    }

    @Test fun onlyReachableResponsesRankByLatency() {
        assertEquals(80L, sourceResponseRank(ProbeStatus.AVAILABLE, 80L))
        assertEquals(Long.MAX_VALUE, sourceResponseRank(ProbeStatus.UNAVAILABLE, 1L))
        assertEquals(Long.MAX_VALUE, sourceResponseRank(ProbeStatus.LIMITED, 1L))
        assertEquals(Long.MAX_VALUE, sourceResponseRank(ProbeStatus.AVAILABLE, null))
        assertEquals(Long.MAX_VALUE, sourceResponseRank(ProbeStatus.AVAILABLE, -1L))
    }

    @Test fun actualViewportDeterminesColumns() {
        assertEquals(360, neonContentWidthPx(400, 20, 20))
        assertEquals(720, neonContentWidthPx(760, 20, 20))
        assertEquals(0, neonContentWidthPx(0, 20, 20))
        assertEquals(0, neonContentWidthPx(30, Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(3, neonColumnCount(3, neonContentWidthPx(400, 20, 20).toFloat()))
        assertEquals(6, neonColumnCount(3, neonContentWidthPx(760, 20, 20).toFloat()))
        assertEquals(3, neonColumnCount(3, 419f))
        assertEquals(4, neonColumnCount(3, 421f))
    }
}
