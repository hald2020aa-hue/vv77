package eu.kanade.tachiyomi.ui.home

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.StorageSeriesType
import eu.kanade.tachiyomi.data.download.StorageSnapshot
import eu.kanade.tachiyomi.data.image.coil.loadManga
import eu.kanade.tachiyomi.databinding.HomeControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.enhanced.EnhancedReadingTheme
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.view.activityBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Storage inventory and explicit classification. Deletion and source refresh are not exposed yet. */
class StorageController : BaseController<HomeControllerBinding>() {
    private val storage by lazy { Injekt.get<DownloadManager>().storageManager }
    private val reload = MutableStateFlow(0L)
    private var snapshot by mutableStateOf<StorageSnapshot?>(null)
    private var loading by mutableStateOf(true)
    private var failed by mutableStateOf(false)
    private var selectedType by mutableStateOf<StorageSeriesType?>(null)
    private var selectedMangaId by mutableStateOf<Long?>(null)
    private var classifyMangaId by mutableStateOf<Long?>(null)
    private var savingType by mutableStateOf(false)
    private var typeSaveFailed by mutableStateOf(false)
    private var topInset by mutableStateOf(0.dp)
    private var bottomInset by mutableStateOf(0.dp)
    private var insetsListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun createBinding(inflater: LayoutInflater) = HomeControllerBinding.inflate(inflater)
    override fun getTitle(): String? = view?.context?.getString(R.string.storage_inventory_title)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        binding.homeCompose.setContent {
            EnhancedReadingTheme {
                val context = LocalContext.current
                val data = snapshot
                val selected = data?.works?.firstOrNull { it.manga.id == selectedMangaId }
                val classifyWork = classifyMangaId?.let { id -> data?.works?.firstOrNull { it.manga.id == id } }
                if (classifyWork != null) {
                    AlertDialog(
                        onDismissRequest = { if (!savingType) classifyMangaId = null },
                        title = { Text(stringResource(R.string.storage_inventory_classify)) },
                        text = {
                            Column {
                                Text(classifyWork.manga.title)
                                val options = listOf(null, StorageSeriesType.MANGA, StorageSeriesType.MANHWA,
                                    StorageSeriesType.MANHUA, StorageSeriesType.UNCLASSIFIED)
                                options.forEach { type ->
                                    val label = when (type) {
                                        null -> R.string.storage_inventory_automatic
                                        StorageSeriesType.MANGA -> R.string.manga
                                        StorageSeriesType.MANHWA -> R.string.manhwa
                                        StorageSeriesType.MANHUA -> R.string.manhua
                                        StorageSeriesType.UNCLASSIFIED -> R.string.storage_inventory_unclassified
                                    }
                                    TextButton(enabled = !savingType, onClick = {
                                        classifyWork.manga.id?.let { saveType(it, type) }
                                    }) {
                                        Text((if (classifyWork.manga.storageManualType == type?.name) "✓ " else "") + stringResource(label))
                                    }
                                }
                                if (savingType) Text(stringResource(R.string.loading))
                                if (typeSaveFailed) Text(stringResource(R.string.storage_inventory_classify_error), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(enabled = !savingType, onClick = { classifyMangaId = null }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        },
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset + 12.dp, bottom = bottomInset + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(stringResource(R.string.storage_inventory_title), style = MaterialTheme.typography.headlineSmall)
                        Button(onClick = { reload.update { it + 1 } }, enabled = !loading) {
                            Text(stringResource(R.string.storage_inventory_refresh))
                        }
                        if (loading) Text(stringResource(R.string.loading))
                        if (failed) Text(stringResource(R.string.storage_inventory_error), color = MaterialTheme.colorScheme.error)
                    }
                    if (data != null && !failed) {
                        item {
                            Text(stringResource(R.string.storage_inventory_summary, data.works.size, data.chapterCount, Formatter.formatFileSize(context, data.bytes)))
                            Text(stringResource(R.string.storage_inventory_scope), style = MaterialTheme.typography.bodySmall)
                        }
                        if (selected != null) {
                            item {
                                Button(onClick = { selectedMangaId = null }) { Text(stringResource(R.string.all)) }
                                Text(selected.manga.title, style = MaterialTheme.typography.titleLarge)
                            }
                            items(selected.chapters.sortedByDescending { it.chapter.source_order }) { entry ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(entry.chapter.name)
                                        Text(Formatter.formatFileSize(context, entry.bytes))
                                        Button(enabled = !loading, onClick = {
                                            context.startActivity(ReaderActivity.newIntent(context, selected.manga, entry.chapter))
                                        }) { Text(stringResource(R.string.continue_reading)) }
                                    }
                                }
                            }
                        } else {
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val options = listOf(null, StorageSeriesType.MANGA, StorageSeriesType.MANHWA) +
                                        listOf(StorageSeriesType.MANHUA).filter { type -> data.works.any { it.type == type } }
                                    items(options) { option ->
                                        FilterChip(
                                            selected = selectedType == option,
                                            onClick = { selectedType = option },
                                            label = { Text(stringResource(when (option) {
                                                StorageSeriesType.MANGA -> R.string.manga
                                                StorageSeriesType.MANHWA -> R.string.manhwa
                                                StorageSeriesType.MANHUA -> R.string.manhua
                                                else -> R.string.all
                                            })) },
                                        )
                                    }
                                }
                            }
                            val visible = data.works.filter { selectedType == null || it.type == selectedType }
                            if (visible.isEmpty()) item { Text(stringResource(R.string.storage_inventory_empty)) }
                            items(visible) { work ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AndroidView(
                                            factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                                            update = { it.contentDescription = work.manga.title; it.loadManga(work.manga) },
                                            modifier = Modifier.size(width = 72.dp, height = 104.dp),
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(work.manga.title, style = MaterialTheme.typography.titleMedium)
                                            Text(stringResource(R.string.storage_inventory_work, work.chapters.size, Formatter.formatFileSize(context, work.bytes)))
                                            Text(stringResource(when (work.type) {
                                                StorageSeriesType.MANGA -> R.string.manga
                                                StorageSeriesType.MANHWA -> R.string.manhwa
                                                StorageSeriesType.MANHUA -> R.string.manhua
                                                StorageSeriesType.UNCLASSIFIED -> R.string.storage_inventory_unclassified
                                            }))
                                            TextButton(enabled = !loading && !savingType, onClick = {
                                                typeSaveFailed = false
                                                classifyMangaId = work.manga.id
                                            }) { Text(stringResource(R.string.storage_inventory_classify)) }
                                            Button(onClick = { selectedMangaId = work.manga.id }) { Text(stringResource(R.string.storage_inventory_chapters)) }
                                            val next = work.chapters.sortedByDescending { it.chapter.source_order }.firstOrNull { !it.chapter.read }
                                            if (next != null) Button(enabled = !loading, onClick = {
                                                context.startActivity(ReaderActivity.newIntent(context, work.manga, next.chapter))
                                            }) { Text(stringResource(R.string.continue_reading)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val density = view.resources.displayMetrics.density
        val updateInsets = {
            topInset = ((activityBinding?.appBar?.height ?: 0) / density).dp
            bottomInset = ((activityBinding?.bottomNav?.height ?: 0) / density).dp
        }
        updateInsets()
        insetsListener = ViewTreeObserver.OnGlobalLayoutListener { updateInsets() }
        view.viewTreeObserver.addOnGlobalLayoutListener(insetsListener)
        viewScope.launch {
            combine(storage.changes, reload) { _, _ -> Unit }.collectLatest {
                loading = true
                failed = false
                flow { emit(storage.snapshot(force = true)) }
                    .catch { error ->
                        Timber.e(error, "Storage inventory failed")
                        failed = true
                        snapshot = null
                    }.collect { snapshot = it }
                loading = false
            }
        }
    }

    private fun saveType(id: Long, type: StorageSeriesType?) {
        if (savingType) return
        savingType = true
        typeSaveFailed = false
        viewScope.launch {
            try {
                flow { storage.setManualType(id, type); emit(Unit) }
                    .catch { error ->
                        Timber.e(error, "Storage classification failed")
                        typeSaveFailed = true
                    }.collect {
                        classifyMangaId = null
                        selectedType = null
                        reload.update { it + 1 }
                    }
            } finally {
                savingType = false
            }
        }
    }

    override fun onDestroyView(view: View) {
        insetsListener?.let { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnGlobalLayoutListener(it) }
        insetsListener = null
        super.onDestroyView(view)
    }
}
