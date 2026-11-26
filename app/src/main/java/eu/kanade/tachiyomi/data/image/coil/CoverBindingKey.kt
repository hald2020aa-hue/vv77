package eu.kanade.tachiyomi.data.image.coil

/** Immutable identity of a request, independent of transfer progress and mutable Manga instances. */
internal data class CoverBindingKey(
    val mangaId: Long?,
    val sourceId: Long,
    val url: String,
    val thumbnailUrl: String?,
    val favorite: Boolean,
    val lastUpdate: Long,
    val widthPx: Int,
    val heightPx: Int,
)
