package eu.kanade.tachiyomi.ui.reader.plus

import android.content.Context
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate

/** Separate private preferences: does not alter manga, chapter, source, or backup schemas. */
internal class PlusReaderStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mv_plus_reader_local_v1", Context.MODE_PRIVATE)

    private fun chapterKey(source: Long, mangaUrl: String, chapterUrl: String): String {
        val identity = "$source:${mangaUrl.length}:$mangaUrl:${chapterUrl.length}:$chapterUrl"
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(StandardCharsets.UTF_8))
        return "marks_" + digest.joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun bookmarks(source: Long, mangaUrl: String, chapterUrl: String, pageCount: Int): List<Int> = synchronized(lock) {
        PlusReaderRules.pages(prefs.getStringSet(chapterKey(source, mangaUrl, chapterUrl), emptySet()).orEmpty(), pageCount)
    }

    fun toggle(source: Long, mangaUrl: String, chapterUrl: String, index: Int, pageCount: Int): Boolean = synchronized(lock) {
        if (index !in 0 until pageCount) return@synchronized false
        val key = chapterKey(source, mangaUrl, chapterUrl)
        val updated = PlusReaderRules.toggle(prefs.getStringSet(key, emptySet()).orEmpty(), index, pageCount)
        prefs.edit().putStringSet(key, updated).apply()
        index.toString() in updated
    }

    fun record(date: LocalDate, durationMs: Long) = synchronized(lock) {
        if (durationMs !in PlusReaderRules.MIN_VISIT_MS..PlusReaderRules.MAX_VISIT_MS) return@synchronized
        val prefix = "day_$date"
        prefs.edit()
            .putLong("${prefix}_visits", PlusReaderRules.safeAdd(prefs.getLong("${prefix}_visits", 0), 1))
            .putLong("${prefix}_ms", PlusReaderRules.safeAdd(prefs.getLong("${prefix}_ms", 0), durationMs))
            .apply()
    }

    data class Totals(val visits: Long, val durationMs: Long) {
        val averageMs: Long get() = if (visits > 0) durationMs / visits else 0
    }

    fun totals(today: LocalDate, days: Int): Totals = synchronized(lock) {
        var visits = 0L
        var duration = 0L
        for (date in PlusReaderRules.datesEnding(today, days)) {
            visits = PlusReaderRules.safeAdd(visits, prefs.getLong("day_${date}_visits", 0))
            duration = PlusReaderRules.safeAdd(duration, prefs.getLong("day_${date}_ms", 0))
        }
        Totals(visits, duration)
    }

    companion object {
        private val lock = Any()
    }
}
