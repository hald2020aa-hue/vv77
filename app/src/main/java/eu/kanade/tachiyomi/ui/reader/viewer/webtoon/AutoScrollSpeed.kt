package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/** Session-only speeds in dp/second, independent of screen density. */
internal fun autoScrollDpPerSecond(index: Int): Int = when (index) {
    0 -> 48
    2 -> 144
    else -> 88
}
