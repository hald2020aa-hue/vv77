package eu.kanade.tachiyomi.ui.reader

/** One-based page number; zero disables speculative adjacent-chapter loads. */
internal fun shouldPreloadAdjacent(pageNumber: Int, pageCount: Int, configuredPages: Int): Boolean {
    if (pageCount <= 0 || pageNumber !in 1..pageCount || configuredPages <= 0) return false
    return pageCount - pageNumber < configuredPages.coerceAtMost(5)
}
