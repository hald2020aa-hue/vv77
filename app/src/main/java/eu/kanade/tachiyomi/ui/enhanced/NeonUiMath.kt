package eu.kanade.tachiyomi.ui.enhanced

import kotlin.math.max
import kotlin.math.roundToInt

/** Exact number of columns on a phone; scaled proportionately on an unfolded display. */
fun neonColumnCount(phoneColumns: Int, availableWidthDp: Float): Int {
    require(phoneColumns == 2 || phoneColumns == 3 || phoneColumns == 5)
    return max(1, (availableWidthDp.coerceAtLeast(160f) / 360f * phoneColumns).roundToInt())
}

/** Chapter progress is separate from the completed-chapter count. Unknown page counts stay unknown. */
fun knownPagePercentage(downloaded: Int, total: Int?): Int? =
    total?.takeIf { it > 0 }?.let { (downloaded.coerceIn(0, it) * 100.0 / it).roundToInt() }

/** ETA is only valid with known true total bytes and a measured positive byte rate. */
fun estimatedTransferSeconds(totalBytes: Long?, received: Long, bytesPerSecond: Long): Long? {
    if (totalBytes == null || totalBytes <= 0L || received < 0L || bytesPerSecond <= 0L) return null
    if (received >= totalBytes) return 0L
    val remaining = totalBytes - received
    // Ceiling division without overflow: a partially received transfer never shows zero seconds.
    return 1L + (remaining - 1L) / bytesPerSecond
}

fun neonChapterPaletteIndex(chapterNumber: Float, paletteCount: Int = 7): Int {
    require(paletteCount > 0)
    return chapterNumber.roundToInt().mod(paletteCount)
}

/** The RecyclerView's own viewport, not the physical display, determines cover width. */
fun neonContentWidthPx(outerWidth: Int, startPadding: Int, endPadding: Int): Int =
    (outerWidth.toLong() - startPadding.coerceAtLeast(0) - endPadding.coerceAtLeast(0))
        .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
