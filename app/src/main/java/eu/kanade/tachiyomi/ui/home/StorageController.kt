package eu.kanade.tachiyomi.ui.home

import android.app.Activity
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.StorageDeleteRequest
import eu.kanade.tachiyomi.data.download.StorageDeleteResult
import eu.kanade.tachiyomi.data.download.StorageSeriesType
import eu.kanade.tachiyomi.data.download.StorageSnapshot
import eu.kanade.tachiyomi.data.image.coil.MangaCoverImageView
import eu.kanade.tachiyomi.databinding.HomeControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.enhanced.EnhancedReadingTheme
import eu.kanade.tachiyomi.ui.main.BottomNavBarInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.isLocal
import kotlinx.coroutines.Job
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

/** Storage inventory, classification, additive refresh and confirmed file-only deletion. */
@OptIn(ExperimentalFoundationApi::class)
class StorageController : BaseController<HomeControllerBinding>(), BottomNavBarInterface {
    private val storage by lazy { Injekt.get<DownloadManager>().storageManager }
    private val reload = MutableStateFlow(0L)
    private var snapshot by mutableStateOf<StorageSnapshot?>(null)
    private var loading by mutableStateOf(true)
    private var failed by mutableStateOf(false)
    private var groupMode by mutableStateOf(false)
    private var selectedCategory by mutableStateOf<Int?>(null)
    private var sortMode by mutableStateOf(0)
    private var selectedType by mutableStateOf<StorageSeriesType?>(null)
    private var selectedMangaId by mutableStateOf<Long?>(null)
    private var selectedWorkIds by mutableStateOf<Set<Long>>(emptySet())
    private var deleteRequests by mutableStateOf<Pair<StorageDeleteRequest, StorageDeleteRequest>?>(null)
    private var deleteReadOnly by mutableStateOf(true)
    private var deleting by mutableStateOf(false)
    private var deletionFailed by mutableStateOf(false)
    private var deletionResult by mutableStateOf<StorageDeleteResult?>(null)
    private var refreshState by mutableStateOf(RefreshState.IDLE)
    private var newChapters by mutableStateOf<List<Chapter>>(emptyList())
    private var refreshJob: Job? = null
    private enum class RefreshState { IDLE, LOADING, OFFLINE, LOCAL, SUCCESS, ERROR }
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
                // The system back callback must follow in-screen navigation even when storage is a root tab.
                LaunchedEffect(canStillGoBack()) {
                    (activity as? MainActivity)?.reEnableBackPressedCallBack()
                }
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
                deleteRequests?.let { (all, read) ->
                    val request = if (deleteReadOnly) read else all
                    AlertDialog(
                        onDismissRequest = { if (!deleting) deleteRequests = null },
                        title = { Text(stringResource(R.string.storage_delete_title)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = deleteReadOnly, enabled = !deleting,
                                    onClick = { deleteReadOnly = true }, label = { Text(stringResource(R.string.storage_delete_read)) })
                                FilterChip(selected = !deleteReadOnly, enabled = !deleting,
                                    onClick = { deleteReadOnly = false }, label = { Text(stringResource(R.string.storage_delete_all)) })
                                Text(stringResource(R.string.storage_delete_estimate, request.targets.size, Formatter.formatFileSize(context, request.bytes)))
                                Text(stringResource(R.string.storage_delete_warning))
                                if (deleting) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        },
                        confirmButton = {
                            TextButton(enabled = !deleting && request.targets.isNotEmpty(), onClick = { executeDeletion(request) }) {
                                Text(stringResource(R.string.storage_delete_confirm), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(enabled = !deleting, onClick = { deleteRequests = null }) {
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
                        Button(onClick = { reload.update { it + 1 } }, enabled = !loading && !deleting) {
                            Text(stringResource(R.string.storage_inventory_refresh))
                        }
                        if (loading) Text(stringResource(R.string.loading))
                        if (failed) Text(stringResource(R.string.storage_inventory_error), color = MaterialTheme.colorScheme.error)
                        if (deleting) Text(stringResource(R.string.storage_delete_running))
                        if (deletionFailed) Text(stringResource(R.string.storage_delete_error), color = MaterialTheme.colorScheme.error)
                        deletionResult?.let { result ->
                            Text(stringResource(R.string.storage_delete_result, result.deletedCount, result.failedCount),
                                color = if (result.failedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (data != null && !failed) {
                        item {
                            Text(stringResource(R.string.storage_inventory_summary, data.works.size, data.chapterCount, Formatter.formatFileSize(context, data.bytes)))
                            Text(stringResource(R.string.storage_inventory_scope), style = MaterialTheme.typography.bodySmall)
                        }
                        if (selected != null) {
                            item {
                                Button(onClick = { closeWork() }) { Text(stringResource(R.string.all)) }
                                Text(selected.manga.title, style = MaterialTheme.typography.titleLarge)
                                if (!selected.manga.isLocal()) TextButton(enabled = !loading && !deleting && refreshState != RefreshState.LOADING,
                                    onClick = { selected.manga.id?.let { prepareDeletion(setOf(it)) } }) {
                                    Text(stringResource(R.string.storage_delete_title))
                                }
                                when (refreshState) {
                                    RefreshState.LOADING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                        Text(stringResource(R.string.storage_chapters_updating))
                                    }
                                    RefreshState.OFFLINE -> {
                                        Text(stringResource(R.string.storage_chapters_offline))
                                        TextButton(onClick = { refreshWork() }) { Text(stringResource(R.string.retry)) }
                                    }
                                    RefreshState.ERROR -> {
                                        Text(stringResource(R.string.storage_chapters_update_error), color = MaterialTheme.colorScheme.error)
                                        TextButton(onClick = { refreshWork() }) { Text(stringResource(R.string.retry)) }
                                    }
                                    RefreshState.SUCCESS -> Text(stringResource(R.string.storage_chapters_new_count, newChapters.size))
                                    RefreshState.LOCAL, RefreshState.IDLE -> Unit
                                }
                            }
                            if (newChapters.isNotEmpty()) {
                                item { Text(stringResource(R.string.new_chapters), style = MaterialTheme.typography.titleMedium) }
                                items(newChapters, key = { "new:${it.id ?: it.url}" }) { chapter ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(chapter.name, style = MaterialTheme.typography.titleMedium)
                                            Text(stringResource(R.string.storage_chapters_new_badge), color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                            item { Text(stringResource(R.string.storage_inventory_chapters), style = MaterialTheme.typography.titleMedium) }
                            items(selected.chapters.sortedByDescending { it.chapter.source_order }, key = { "download:${it.chapter.id ?: it.chapter.url}" }) { entry ->
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
                                            onClick = { selectedType = option; selectedCategory = null; selectedWorkIds = emptySet() },
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
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = !groupMode, onClick = {
                                        groupMode = false; selectedCategory = null; selectedWorkIds = emptySet()
                                    }, label = { Text(stringResource(R.string.storage_inventory_by_work)) })
                                    FilterChip(selected = groupMode, onClick = {
                                        groupMode = true; selectedCategory = null; selectedWorkIds = emptySet()
                                    }, label = { Text(stringResource(R.string.storage_inventory_by_category)) })
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val sorts = listOf(R.string.storage_inventory_sort_size, R.string.storage_inventory_sort_read, R.string.storage_inventory_sort_file)
                                    items(sorts.indices.toList()) { mode ->
                                        FilterChip(selected = sortMode == mode, onClick = { sortMode = mode }, label = { Text(stringResource(sorts[mode])) })
                                    }
                                }
                            }
                            val typed = data.works.filter { selectedType == null || it.type == selectedType }
                            val categories = typed.flatMap { work ->
                                work.categories.mapNotNull { category -> category.id?.let { it to category.name } }
                            }.distinctBy { it.first }.sortedBy { it.second }
                            // Works with no explicit category go in a separate group. Multi-category works
                            // appear in each category; the global total is always counted only once.
                            val groups = categories + if (typed.any { it.categories.none { c -> c.id != null } }) {
                                listOf(Int.MIN_VALUE to context.getString(R.string.storage_inventory_no_category))
                            } else emptyList()
                            if (groupMode && selectedCategory == null) {
                                item { Text(stringResource(R.string.storage_inventory_category_note), style = MaterialTheme.typography.bodySmall) }
                                items(groups, key = { it.first }) { (id, name) ->
                                    val works = typed.filter { work ->
                                        if (id == Int.MIN_VALUE) work.categories.none { it.id != null } else work.categories.any { it.id == id }
                                    }
                                    val bytes = works.sumOf { it.bytes }
                                    val ratio = if (data.bytes > 0L) (bytes.toDouble() / data.bytes).toFloat().coerceIn(0f, 1f) else 0f
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(name, style = MaterialTheme.typography.titleMedium)
                                            Text(stringResource(R.string.storage_inventory_summary, works.size, works.sumOf { it.chapters.size }, Formatter.formatFileSize(context, bytes)))
                                            LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                                            Text("${(ratio * 100).toInt()}%")
                                            TextButton(onClick = { selectedCategory = id; selectedWorkIds = emptySet() }) { Text(stringResource(R.string.storage_inventory_open_category)) }
                                            val deletable = works.filterNot { it.manga.isLocal() }.mapNotNull { it.manga.id }.toSet()
                                            TextButton(enabled = !loading && !deleting && deletable.isNotEmpty(), onClick = { prepareDeletion(deletable) }) {
                                                Text(stringResource(R.string.storage_delete_category))
                                            }
                                        }
                                    }
                                }
                                if (groups.isEmpty()) item { Text(stringResource(R.string.storage_inventory_empty)) }
                            }
                            if (groupMode && selectedCategory != null) item {
                                TextButton(onClick = { selectedCategory = null; selectedWorkIds = emptySet() }) { Text(stringResource(R.string.storage_inventory_by_category)) }
                                Text(groups.firstOrNull { it.first == selectedCategory }?.second.orEmpty(), style = MaterialTheme.typography.titleLarge)
                            }
                            val filtered = typed.filter { work ->
                                !groupMode || when (val category = selectedCategory) {
                                    null -> false
                                    Int.MIN_VALUE -> work.categories.none { it.id != null }
                                    else -> work.categories.any { it.id == category }
                                }
                            }
                            val visible = when (sortMode) {
                                1 -> filtered.sortedByDescending { it.lastReadAt }
                                2 -> filtered.sortedBy { it.oldestFileAt ?: Long.MAX_VALUE }
                                else -> filtered.sortedByDescending { it.bytes }
                            }
                            if (visible.isEmpty() && (!groupMode || selectedCategory != null)) item { Text(stringResource(R.string.storage_inventory_empty)) }
                            if (visible.isNotEmpty()) item {
                                val ids = visible.filterNot { it.manga.isLocal() }.mapNotNull { it.manga.id }.toSet()
                                if (selectedWorkIds.isNotEmpty()) Text(stringResource(R.string.storage_delete_selected, selectedWorkIds.size))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    item {
                                        TextButton(enabled = !loading && !deleting && ids.isNotEmpty(), onClick = { selectedWorkIds = ids }) {
                                            Text(stringResource(R.string.storage_delete_select_all))
                                        }
                                    }
                                    if (selectedWorkIds.isNotEmpty()) {
                                        item { TextButton(enabled = !deleting, onClick = { selectedWorkIds = emptySet() }) { Text(stringResource(android.R.string.cancel)) } }
                                        item { TextButton(enabled = !loading && !deleting, onClick = { prepareDeletion(selectedWorkIds) }) { Text(stringResource(R.string.storage_delete_title)) } }
                                    }
                                }
                            }
                            items(visible, key = { "work:${it.manga.id ?: "${it.manga.source}:${it.manga.url}"}" }) { work ->
                                val workId = work.manga.id
                                val canSelect = !work.manga.isLocal() && workId != null
                                Card(modifier = Modifier.fillMaxWidth().combinedClickable(
                                    enabled = !loading && !deleting,
                                    onClick = { if (selectedWorkIds.isNotEmpty() && canSelect && workId != null) toggleSelection(workId) else openWork(workId) },
                                    onLongClick = { if (canSelect && workId != null) toggleSelection(workId) },
                                )) {
                                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AndroidView(
                                            factory = { MangaCoverImageView(it) },
                                            onReset = null,
                                            onRelease = { it.releaseCover() },
                                            update = { it.bindCover(work.manga) },
                                            modifier = Modifier.size(width = 72.dp, height = 104.dp),
                                        )
                                        Column(Modifier.weight(1f)) {
                                            if (canSelect && workId != null) Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = workId in selectedWorkIds, enabled = !loading && !deleting,
                                                    onCheckedChange = { toggleSelection(workId) })
                                                Text(stringResource(R.string.storage_delete_select_work))
                                            }
                                            Text(work.manga.title, style = MaterialTheme.typography.titleMedium)
                                            Text(stringResource(R.string.storage_inventory_work, work.chapters.size, Formatter.formatFileSize(context, work.bytes)))
                                            val fraction = if (data.bytes > 0L) (work.bytes.toDouble() / data.bytes).toFloat().coerceIn(0f, 1f) else 0f
                                            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
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
                                            Button(onClick = { openWork(work.manga.id) }) { Text(stringResource(R.string.storage_inventory_chapters)) }
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
            val mainActivity = activity as? MainActivity
            val appBar = activityBinding?.appBar
            val bottomNav = activityBinding?.bottomNav
            val top = appBar?.takeIf { it.isVisible }?.height ?: mainActivity?.cachedSystemInsets?.top ?: 0
            val bottom = bottomNav?.takeIf { it.isVisible }?.height ?: mainActivity?.cachedSystemInsets?.bottom ?: 0
            topInset = (top / density).dp
            bottomInset = (bottom / density).dp
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
                    }.collect {
                        snapshot = it
                        selectedWorkIds = selectedWorkIds.intersect(it.works.mapNotNull { work -> work.manga.id }.toSet())
                        if (selectedMangaId != null && it.works.none { work -> work.manga.id == selectedMangaId }) closeWork()
                    }
                loading = false
            }
        }
    }

    private fun toggleSelection(id: Long) {
        if (deleting) return
        selectedWorkIds = if (id in selectedWorkIds) selectedWorkIds - id else selectedWorkIds + id
    }

    private fun prepareDeletion(ids: Set<Long>) {
        if (loading || deleting || ids.isEmpty()) return
        val data = snapshot ?: return
        val all = data.deletionRequest(ids, readOnly = false)
        if (all.targets.isEmpty()) return
        deleteReadOnly = true
        deletionFailed = false
        deletionResult = null
        deleteRequests = all to data.deletionRequest(ids, readOnly = true)
    }

    private fun executeDeletion(request: StorageDeleteRequest) {
        if (deleting || request.targets.isEmpty()) return
        deleting = true
        deletionFailed = false
        deletionResult = null
        viewScope.launch {
            try {
                flow { emit(storage.deleteDownloads(request)) }
                    .catch { error ->
                        Timber.e(error, "Storage deletion rejected or failed")
                        deletionFailed = true
                    }.collect { result -> deletionResult = result }
            } finally {
                deleting = false
                deleteRequests = null
                selectedWorkIds = emptySet()
                reload.update { it + 1 }
            }
        }
    }

    private fun openWork(id: Long?) {
        if (id == null || deleting) return
        selectedWorkIds = emptySet()
        closeWork()
        selectedMangaId = id
        refreshWork()
    }

    private fun closeWork() {
        refreshJob?.cancel()
        refreshJob = null
        selectedMangaId = null
        refreshState = RefreshState.IDLE
        newChapters = emptyList()
    }

    private fun refreshWork() {
        val id = selectedMangaId ?: return
        if (refreshJob?.isActive == true) return
        val work = snapshot?.works?.firstOrNull { it.manga.id == id } ?: return
        if (work.manga.isLocal()) {
            refreshState = RefreshState.LOCAL
            return
        }
        if (view?.context?.isOnline() != true) {
            refreshState = RefreshState.OFFLINE
            return
        }
        refreshState = RefreshState.LOADING
        refreshJob = viewScope.launch {
            flow { emit(storage.refreshChapters(id)) }
                .catch { error ->
                    Timber.e(error, "Storage chapter update failed")
                    if (selectedMangaId == id) refreshState = RefreshState.ERROR
                }.collect { chapters ->
                    if (selectedMangaId == id) {
                        newChapters = (newChapters + chapters).distinctBy { it.url }
                        refreshState = RefreshState.SUCCESS
                    }
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

    override fun canChangeTabs(block: () -> Unit): Boolean = !deleting && !savingType

    override fun canStillGoBack(): Boolean = deleting || selectedWorkIds.isNotEmpty() || selectedMangaId != null || selectedCategory != null

    override fun handleBack(): Boolean {
        if (deleting) return true
        if (selectedWorkIds.isNotEmpty()) {
            selectedWorkIds = emptySet()
            return true
        }
        if (selectedMangaId != null) {
            closeWork()
            return true
        }
        if (selectedCategory != null) {
            selectedCategory = null
            return true
        }
        return super.handleBack()
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        reload.update { it + 1 }
    }

    override fun onDestroyView(view: View) {
        closeWork()
        insetsListener?.let { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnGlobalLayoutListener(it) }
        insetsListener = null
        super.onDestroyView(view)
    }
}
