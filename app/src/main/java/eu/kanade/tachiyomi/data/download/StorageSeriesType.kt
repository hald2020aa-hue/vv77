package eu.kanade.tachiyomi.data.download

/** Only explicit source tags are accepted. Ambiguous tags are not guessed. */
enum class StorageSeriesType {
    MANGA, MANHWA, MANHUA, UNCLASSIFIED;

    companion object {
        fun fromTags(tags: List<String>?): StorageSeriesType =
            tags.orEmpty().mapNotNull {
                when (it.trim().lowercase()) {
                    "manga" -> MANGA
                    "manhwa" -> MANHWA
                    "manhua" -> MANHUA
                    else -> null
                }
            }.distinct().singleOrNull() ?: UNCLASSIFIED
    }
}
