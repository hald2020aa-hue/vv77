package eu.kanade.tachiyomi.data.image.coil

import android.content.Context
import android.widget.ImageView
import coil.dispose
import eu.kanade.tachiyomi.data.database.models.Manga

/**
 * A cover target for Compose AndroidView rows. Progress and selection recompositions must not
 * enqueue the same image again. Requests use the measured size and the original cover loader.
 * Detaching releases the request; attaching again refreshes the cover, including custom covers.
 */
internal class MangaCoverImageView(context: Context) : ImageView(context) {
    private var boundManga: Manga? = null
    private var lastRequest: CoverBindingKey? = null

    init {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    fun bindCover(manga: Manga, title: String = manga.title) {
        boundManga = manga
        contentDescription = title
        requestCover(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        requestCover(w, h)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestCover(width, height)
    }

    override fun onDetachedFromWindow() {
        dispose()
        lastRequest = null
        super.onDetachedFromWindow()
    }

    fun releaseCover() {
        dispose()
        boundManga = null
        lastRequest = null
        setImageDrawable(null)
    }

    private fun requestCover(w: Int, h: Int) {
        val manga = boundManga ?: return
        val measuredWidth = w - paddingLeft - paddingRight
        val measuredHeight = h - paddingTop - paddingBottom
        if (!isAttachedToWindow || measuredWidth <= 0 || measuredHeight <= 0) return
        val request = CoverBindingKey(
            manga.id, manga.source, manga.url, manga.thumbnail_url, manga.favorite,
            manga.last_update, measuredWidth, measuredHeight,
        )
        if (request == lastRequest) return
        // Coil cancels the previous request on this ImageView when a new one is enqueued.
        loadManga(manga) { size(measuredWidth, measuredHeight) }
        lastRequest = request
    }
}
