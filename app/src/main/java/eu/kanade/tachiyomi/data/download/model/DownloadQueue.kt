package eu.kanade.tachiyomi.data.download.model

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

class DownloadQueue(
    private val store: DownloadStore,
    private val queue: MutableList<Download> = CopyOnWriteArrayList<Download>(),
) : List<Download> by queue {
    private val _state = MutableStateFlow(emptyList<Download>())

    /** Emits the queue's contents on every change, so the downloader can re-pick what to run. */
    val state: StateFlow<List<Download>> = _state.asStateFlow()

    private val downloadListeners = CopyOnWriteArrayList<DownloadListener>()

    private val scope = MainScope()
    // A chapter may be paused and resumed many times. Own exactly one set of page observers.
    private val pageObservers = mutableMapOf<Download, List<Job>>()

    fun addAll(downloads: List<Download>) {
        downloads.forEach { download ->
            download.setStatusCallback(::setPagesFor)
            download.status = Download.State.QUEUE
        }
        queue.addAll(downloads)
        store.replaceAll(queue.toList())
        _state.value = queue.toList()
    }

    /** Move the same Download objects without clearing their page progress or persisted queue. */
    fun reorder(downloads: List<Download>) {
        require(downloads.size == queue.size && downloads.toSet() == queue.toSet()) {
            "Cannot reorder with missing or unknown downloads"
        }
        queue.clear()
        queue.addAll(downloads)
        store.replaceAll(queue.toList())
        _state.value = queue.toList()
        updateListeners()
    }

    fun remove(download: Download) {
        val removed = queue.remove(download)
        store.remove(download)
        download.setStatusCallback(null)
        stopPageObservers(download)
        if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
            download.status = Download.State.NOT_DOWNLOADED
        }
        downloadListeners.forEach { it.updateDownload(download) }
        if (removed) {
            _state.value = queue.toList()
        }
    }

    fun updateListeners() {
        val listeners = downloadListeners.toList()
        listeners.forEach { it.updateDownloads() }
    }

    fun remove(chapter: Chapter) {
        find { it.chapter.id == chapter.id }?.let { remove(it) }
    }

    fun remove(chapters: List<Chapter>) {
        for (chapter in chapters) {
            remove(chapter)
        }
    }

    fun remove(manga: Manga) {
        filter { it.manga.id == manga.id }.forEach { remove(it) }
    }

    fun clear() {
        queue.forEach { download ->
            download.setStatusCallback(null)
            if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                download.status = Download.State.NOT_DOWNLOADED
            }
            downloadListeners.forEach { it.updateDownload(download) }
        }
        queue.clear()
        stopAllPageObservers()
        store.clear()
        _state.value = queue.toList()
    }

    @Synchronized
    private fun stopPageObservers(download: Download) {
        pageObservers.remove(download)?.forEach { it.cancel() }
    }

    @Synchronized
    private fun stopAllPageObservers() {
        pageObservers.values.flatten().forEach { it.cancel() }
        pageObservers.clear()
    }

    private fun setPagesFor(download: Download) {
        synchronized(this) {
            if (download.status == Download.State.DOWNLOADING && download in queue) {
                if (download !in pageObservers) {
                    val pages = download.pages.orEmpty()
                    if (pages.isNotEmpty()) {
                        pageObservers[download] = pages.map { page ->
                            scope.launch {
                                page.statusFlow.collectLatest {
                                    if (download.status == Download.State.DOWNLOADING && download in queue) {
                                        callListeners(download)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                stopPageObservers(download)
            }
        }
        if (download.status != Download.State.DOWNLOADED) callListeners(download)
    }

    private fun callListeners(download: Download) {
        downloadListeners.forEach { it.updateDownload(download) }
    }

    fun addListener(listener: DownloadListener) {
        downloadListeners.addIfAbsent(listener)
    }

    fun removeListener(listener: DownloadListener) {
        downloadListeners.remove(listener)
    }

    interface DownloadListener {
        fun updateDownload(download: Download)

        fun updateDownloads()
    }
}
