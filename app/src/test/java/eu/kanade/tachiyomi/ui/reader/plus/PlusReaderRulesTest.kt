package eu.kanade.tachiyomi.ui.reader.plus

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PlusReaderRulesTest {
    @Test fun bookmarks() {
        assertEquals(listOf(0, 2), PlusReaderRules.pages(setOf("0", "2", "-1", "99", "bad"), 3))
        assertEquals(setOf("1", "2"), PlusReaderRules.toggle(setOf("1"), 2, 3))
        assertEquals(setOf("1"), PlusReaderRules.toggle(setOf("1", "2"), 2, 3))
        assertEquals(setOf("1"), PlusReaderRules.toggle(setOf("1"), 3, 3))
    }
    @Test fun duration() {
        assertEquals(0L, PlusReaderRules.dwellMs(1000, 1999))
        assertEquals(1000L, PlusReaderRules.dwellMs(1000, 2000))
        assertEquals(120000L, PlusReaderRules.dwellMs(1000, 200000))
        assertEquals(0L, PlusReaderRules.dwellMs(2000, 1000))
    }
    @Test fun tallPageHint() {
        assertEquals(true, PlusReaderRules.isTallPage(800, 2400))
        assertEquals(false, PlusReaderRules.isTallPage(800, 1200))
        assertEquals(false, PlusReaderRules.isTallPage(0, 2400))
        assertEquals(false, PlusReaderRules.isTallPage(Int.MAX_VALUE, Int.MAX_VALUE))
    }
    @Test fun periodsAndOverflow() {
        assertEquals(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 12, 31)), PlusReaderRules.datesEnding(LocalDate.of(2026, 1, 1), 2))
        assertEquals(emptyList<LocalDate>(), PlusReaderRules.datesEnding(LocalDate.now(), 0))
        assertEquals(Long.MAX_VALUE, PlusReaderRules.safeAdd(Long.MAX_VALUE - 1, 9))
        assertEquals(4L, PlusReaderRules.safeAdd(-1, 4))
    }
}
