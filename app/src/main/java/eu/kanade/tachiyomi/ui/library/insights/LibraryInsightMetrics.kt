package eu.kanade.tachiyomi.ui.library.insights

import eu.kanade.tachiyomi.ui.library.search.DuplicateCandidate
import eu.kanade.tachiyomi.ui.library.search.crossSourceDuplicateIds

/** Keep aggregations independent of Android UI so thousands of titles can be regression tested. */
internal data class LibraryInsightEntry(
    val id: Long,
    val sourceId: Long,
    val title: String,
    val unread: Int,
    val read: Int,
    val completed: Boolean,
)

internal data class LibraryInsightMetrics(
    val titles: Int,
    val unreadChapters: Int,
    val readChapters: Int,
    val sources: Int,
    val likelyDuplicateTitles: Int,
    val completedTitles: Int,
    val notStartedTitles: Int,
    val inProgressTitles: Int,
    val caughtUpTitles: Int,
    val unknownChapterTitles: Int,
    val readFraction: Float?,
)

internal fun calculateLibraryInsights(entries: Collection<LibraryInsightEntry>): LibraryInsightMetrics {
    val unique = entries.distinctBy { it.id }
    val duplicateIds = crossSourceDuplicateIds(unique.map { DuplicateCandidate(it.id, it.title, it.sourceId) })
    val read = unique.sumOf { it.read.coerceAtLeast(0).toLong() }
    val unread = unique.sumOf { it.unread.coerceAtLeast(0).toLong() }
    return LibraryInsightMetrics(
        titles = unique.size,
        unreadChapters = unread.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        readChapters = read.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        sources = unique.map { it.sourceId }.distinct().size,
        likelyDuplicateTitles = duplicateIds.size,
        completedTitles = unique.count { it.completed },
        notStartedTitles = unique.count { it.read == 0 && it.unread > 0 },
        inProgressTitles = unique.count { it.read > 0 && it.unread > 0 },
        caughtUpTitles = unique.count { it.read > 0 && it.unread == 0 },
        unknownChapterTitles = unique.count { it.unread < 0 || it.read < 0 || (it.read == 0 && it.unread == 0) },
        readFraction = if (read + unread > 0) (read.toDouble() / (read + unread)).toFloat() else null,
    )
}
