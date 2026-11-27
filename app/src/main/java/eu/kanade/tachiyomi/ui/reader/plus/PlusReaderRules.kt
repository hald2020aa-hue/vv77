package eu.kanade.tachiyomi.ui.reader.plus

import java.time.LocalDate

/** Independent, local-only page marking and foreground reading metrics. */
internal object PlusReaderRules {
    const val MIN_VISIT_MS = 1_000L
    const val MAX_VISIT_MS = 120_000L

    fun pages(values: Set<String>, pageCount: Int): List<Int> =
        values.mapNotNull(String::toIntOrNull).filter { it >= 0 && it < pageCount }.distinct().sorted()

    fun toggle(values: Set<String>, index: Int, pageCount: Int): Set<String> {
        if (index !in 0 until pageCount) return values.toSet()
        return values.toMutableSet().apply {
            if (!remove(index.toString())) add(index.toString())
        }
    }

    fun dwellMs(start: Long, end: Long): Long =
        if (start < 0L || end < start || end - start < MIN_VISIT_MS) 0L
        else (end - start).coerceAtMost(MAX_VISIT_MS)

    fun datesEnding(today: LocalDate, count: Int): List<LocalDate> =
        (0 until count.coerceIn(0, 365)).map { today.minusDays(it.toLong()) }

    /** A conservative hint for very tall pages, not a change to the selected reader mode. */
    fun isTallPage(width: Int, height: Int): Boolean =
        width > 0 && height > 0 && height.toLong() >= 3L * width

    fun safeAdd(a: Long, b: Long): Long {
        val left = a.coerceAtLeast(0L)
        val right = b.coerceAtLeast(0L)
        return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }
}
