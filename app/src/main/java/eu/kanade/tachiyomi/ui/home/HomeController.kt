package eu.kanade.tachiyomi.ui.home

import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.BuildConfig
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.MangaCoverImageView
import eu.kanade.tachiyomi.databinding.HomeControllerBinding
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.download.center.DownloadCenterActivity
import eu.kanade.tachiyomi.ui.enhanced.EnhancedReadingTheme
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.RootSearchInterface
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.util.system.executeOnIO
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * MANGA VIRUS home dashboard. Every title and cover comes from the user's existing
 * library or reading history; this screen never inserts sample catalogue records.
 */
class HomeController : BaseController<HomeControllerBinding>(), RootSearchInterface, FloatingSearchInterface {
    private var libraryManga by mutableStateOf<List<Manga>>(emptyList())
    private var recentManga by mutableStateOf<List<MangaChapterHistory>>(emptyList())
    private var sourceCount by mutableIntStateOf(0)
    private var genreTags by mutableStateOf<List<String>>(emptyList())
    private var loading by mutableStateOf(true)
    private var topInsetDp by mutableStateOf(0.dp)
    private var bottomInsetDp by mutableStateOf(0.dp)
    private var insetsListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private val db: DatabaseHelper by lazy { Injekt.get() }
    private val sourceManager: SourceManager by lazy { Injekt.get() }

    override fun createBinding(inflater: LayoutInflater) = HomeControllerBinding.inflate(inflater)

    override fun getTitle(): String? = view?.context?.getString(R.string.home)

    override fun getBigIcon(): Drawable? = if (BuildConfig.FLAVOR == "dev" && BuildConfig.DESIGN_EDITION == "neon") null else activity?.let { ContextCompat.getDrawable(it, R.mipmap.ic_launcher) }

    override fun getSearchTitle(): String? = searchTitle(getTitle()?.lowercase())

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        (binding.homeCompose as ComposeView).setContent {
            EnhancedReadingTheme {
                HomeDashboard(
                    library = libraryManga,
                    recent = recentManga,
                    sourceCount = sourceCount,
                    genres = genreTags,
                    loading = loading,
                    topInset = topInsetDp,
                    bottomInset = bottomInsetDp,
                    onOpenManga = ::openManga,
                    onContinueReading = { entry ->
                        view.context.startActivity(ReaderActivity.newIntent(view.context, entry.manga, entry.chapter))
                    },
                    onOpenLibrary = { navigateTo(R.id.nav_library) },
                    onOpenRecents = { navigateTo(R.id.nav_recents) },
                    onBrowse = { navigateTo(R.id.nav_browse) },
                    onStorage = { navigateTo(R.id.nav_storage) },
                    onDownloads = {
                        view.context.startActivity(Intent(view.context, DownloadCenterActivity::class.java))
                    },
                )
            }
        }
        // The Compose list is not driven by scrollViewWith(), so pad it below the app bar and above
        // the floating bottom bar by hand and keep the values current when either one resizes.
        val density = view.resources.displayMetrics.density
        val updateInsets = {
            topInsetDp = ((activityBinding?.appBar?.height ?: 0) / density).dp
            bottomInsetDp = ((activityBinding?.bottomNav?.height ?: 0) / density).dp
        }
        updateInsets()
        insetsListener = ViewTreeObserver.OnGlobalLayoutListener { updateInsets() }
        view.viewTreeObserver.addOnGlobalLayoutListener(insetsListener)
        viewScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val library = db.getFavoriteMangas().executeOnIO().sortedByDescending { it.date_added }
                    val recent = db.getRecentMangaLimit("", 0, false).executeOnIO()
                        .distinctBy { it.manga.id ?: "${it.manga.source}:${it.manga.url}" }
                        .take(12)
                    val genres = (library + recent.map { it.manga })
                        .flatMap { it.getGenres().orEmpty() }
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(8)
                    Triple(library, recent, genres)
                }
                libraryManga = data.first
                recentManga = data.second
                genreTags = data.third
                sourceCount = sourceManager.getCatalogueSources().size
            } catch (error: Exception) {
                Timber.e(error, "Failed to load MANGA VIRUS home dashboard")
            } finally {
                loading = false
            }
        }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        // Home has no list that drives the app bar, so reset whatever state the previous tab left behind.
        activityBinding?.appBar?.apply {
            if (BuildConfig.FLAVOR == "dev" && BuildConfig.DESIGN_EDITION == "neon") {
                setToolbarModeBy(this@HomeController)
                hideBigView(false)
            } else {
                setToolbarModeBy(this@HomeController)
                hideBigView(true)
            }
            y = 0f
            lockYPos = true
        }
    }

    override fun onDestroyView(view: View) {
        insetsListener?.let { listener ->
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
        insetsListener = null
        super.onDestroyView(view)
    }

    private fun openManga(manga: Manga) {
        router.pushController(MangaDetailsController(manga).withFadeTransaction())
    }

    private fun navigateTo(itemId: Int) {
        if (activity !is MainActivity) return
        val navigation = activityBinding?.bottomNav ?: activityBinding?.sideNav
        navigation?.selectedItemId = itemId
    }
}

private val homeBackground = Color(0xFF05030F)
private val homePanel = Color(0xFF0E0B27)
private val homePanelRaised = Color(0xFF1B1442)
private val homeText = Color(0xFFFFFFFF)
private val homeMuted = Color(0xFFB3ABD6)
private val homeCyan = Color(0xFF19E6FF)
private val homeBlue = Color(0xFF3D7CFF)
private val homePurple = Color(0xFFA14BFF)
private val homePink = Color(0xFFFF2FD0)

private val homeNeonBorder = Brush.horizontalGradient(listOf(homeCyan, homePurple, homePink))

@Composable
private fun HomeDashboard(
    library: List<Manga>,
    recent: List<MangaChapterHistory>,
    sourceCount: Int,
    genres: List<String>,
    loading: Boolean,
    topInset: Dp,
    bottomInset: Dp,
    onOpenManga: (Manga) -> Unit,
    onContinueReading: (MangaChapterHistory) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenRecents: () -> Unit,
    onBrowse: () -> Unit,
    onDownloads: () -> Unit,
    onStorage: () -> Unit,
) {
    val context = LocalContext.current
    val featured = recent.firstOrNull()?.manga ?: library.firstOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(homeBackground),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topInset + 10.dp, bottom = bottomInset + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            if (featured != null) {
                HomeFeaturedCard(featured, recent.isNotEmpty(), onClick = { onOpenManga(featured) })
            } else {
                HomeEmptyCard(loading = loading, onBrowse = onBrowse)
            }
        }

        if (genres.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 3.dp),
                ) {
                    itemsIndexed(genres) { index, genre ->
                        HomeGenreChip(genre, index)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HomeAction(R.drawable.ic_library_outline_24dp, context.getString(R.string.library), library.size.toString(), homePurple, Modifier.weight(1f), onOpenLibrary)
                HomeAction(R.drawable.ic_history_24dp, context.getString(R.string.updates), null, homePink, Modifier.weight(1f), onOpenRecents)
                HomeAction(R.drawable.ic_browse_outline_24dp, context.getString(R.string.browse), sourceCount.toString(), homeCyan, Modifier.weight(1f), onBrowse)
                HomeAction(R.drawable.ic_file_download_24dp, context.getString(R.string.downloads), null, homeBlue, Modifier.weight(1f), onDownloads)
            }
        }

        item {
            HomeGradientButton(context.getString(R.string.storage_inventory_title), onStorage, Modifier.fillMaxWidth())
        }

        item {
            HomeSectionHeader(context.getString(R.string.continue_reading), recent.size.takeIf { it > 0 }, onOpenRecents)
        }
        if (recent.isEmpty()) {
            item { HomeEmptyHint(if (loading) context.getString(R.string.loading) else context.getString(R.string.home_no_recent)) }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.take(3).forEach { entry ->
                        androidx.compose.runtime.key(entry.manga.id ?: "${entry.manga.source}:${entry.manga.url}") {
                            HomeRecentCard(entry, onClick = { onContinueReading(entry) })
                        }
                    }
                }
            }
        }

        item {
            HomeSectionHeader(context.getString(R.string.v22_home_recently_added), library.size.takeIf { it > 0 }, onOpenLibrary)
        }
        if (library.isEmpty()) {
            item { HomeEmptyHint(if (loading) context.getString(R.string.loading) else context.getString(R.string.home_empty_library)) }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 3.dp)) {
                    items(library.take(12), key = { "library:${it.id ?: "${it.source}:${it.url}"}" }) { manga ->
                        HomePosterCard(manga, onClick = { onOpenManga(manga) })
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onBrowse),
                shape = RoundedCornerShape(18.dp),
                color = homePanel,
                border = BorderStroke(1.2.dp, homeNeonBorder),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(context.getString(R.string.browse), color = homeText, fontWeight = FontWeight.Bold)
                        Text(context.getString(R.string.home_sources_available, sourceCount), color = homeMuted, fontSize = 12.sp)
                    }
                    HomeGradientButton(context.getString(R.string.browse), onBrowse)
                }
            }
        }
    }
}

@Composable
private fun HomeGradientButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Color(0xFF087B9E), Color(0xFF304DBB), Color(0xFF8A24D8))))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun HomeGenreChip(label: String, index: Int) {
    val selectedLook = index == 0
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selectedLook) Color(0xFF7B2CFF) else homePanel,
        border = BorderStroke(1.dp, if (selectedLook) Brush.horizontalGradient(listOf(homePurple, homePink)) else Brush.horizontalGradient(listOf(homeCyan.copy(alpha = 0.55f), homePink.copy(alpha = 0.55f)))),
    ) {
        Text(
            label,
            color = homeText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun HomeAction(icon: Int, label: String, value: String?, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.heightIn(min = 74.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = homePanel,
        border = BorderStroke(1.2.dp, accent.copy(alpha = 0.8f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            androidx.compose.material3.Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = homeText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (value != null) Text(value, color = homeText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HomeFeaturedCard(manga: Manga, isContinueReading: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    // The hero artwork is decorative; the title/author/cover are the real manga record.
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LocalLayoutDirection.current) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(206.dp).clickable(onClick = onClick),
            shape = RoundedCornerShape(22.dp),
            color = homePanel,
            border = BorderStroke(1.6.dp, homeNeonBorder),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(R.drawable.v22_hero_art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.CenterEnd,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(listOf(Color(0xF205030F), Color(0xC005030F), Color(0x2005030F))),
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF3A1B6E),
                            border = BorderStroke(1.dp, homePurple),
                        ) {
                            Text(
                                if (isContinueReading) context.getString(R.string.continue_reading) else context.getString(R.string.library),
                                color = homeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Text(manga.title, color = homeText, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val subtitle = manga.author?.takeIf { it.isNotBlank() } ?: manga.artist?.takeIf { it.isNotBlank() }
                        if (subtitle != null) Text(subtitle, color = homeMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        HomeGradientButton(context.getString(R.string.open), onClick)
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = homePanelRaised,
                        border = BorderStroke(1.2.dp, homeNeonBorder),
                    ) {
                        MangaCover(manga, Modifier.size(width = 102.dp, height = 148.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeEmptyCard(loading: Boolean, onBrowse: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = homePanel,
        border = BorderStroke(1.4.dp, homeNeonBorder),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(LocalContext.current.getString(R.string.app_name), color = homeCyan, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(
                if (loading) LocalContext.current.getString(R.string.loading) else LocalContext.current.getString(R.string.home_empty_library),
                color = homeText,
                fontSize = 14.sp,
            )
            if (!loading) {
                HomeGradientButton(LocalContext.current.getString(R.string.browse), onBrowse)
            }
        }
        Image(painterResource(R.drawable.mv_neon_books), contentDescription = null, modifier = Modifier.widthIn(max = 108.dp).size(94.dp), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun HomeRecentCard(entry: MangaChapterHistory, onClick: () -> Unit) {
    val context = LocalContext.current
    val chapter = entry.chapter
    val total = chapter.last_page_read.toLong() + chapter.pages_left.toLong()
    val progress = if (chapter.read) 1f else if (total > 0) ((chapter.last_page_read.toLong() + 1).toDouble() / total).toFloat().coerceIn(0f, 1f) else null
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(14.dp), color = homePanel, border = BorderStroke(1.dp, homeNeonBorder)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MangaCover(entry.manga, Modifier.size(width = 68.dp, height = 88.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(entry.manga.title, color = homeText, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(chapter.name, color = homeMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (entry.history.last_read > 0) Text(DateUtils.getRelativeTimeSpanString(entry.history.last_read, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(), color = homeMuted, fontSize = 11.sp)
                if (progress != null) {
                    Text(java.text.NumberFormat.getPercentInstance().format(progress.toDouble()), color = homeCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp), color = homeCyan, trackColor = homePanelRaised)
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String, count: Int?, onSeeAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = homeText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (count != null) Text(count.toString(), color = homeCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (onSeeAll != null) {
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = homePanel,
                border = BorderStroke(1.dp, homeNeonBorder),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onSeeAll),
            ) {
                Text(
                    LocalContext.current.getString(R.string.home_see_all),
                    color = homeText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun HomePosterCard(manga: Manga, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(112.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(15.dp),
            color = homePanelRaised,
            border = BorderStroke(1.2.dp, homeNeonBorder),
        ) {
            MangaCover(manga, Modifier.fillMaxWidth().height(150.dp))
        }
        Text(manga.title, color = homeText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val subtitle = manga.author?.takeIf { it.isNotBlank() } ?: manga.artist?.takeIf { it.isNotBlank() }
        if (subtitle != null) Text(subtitle, color = homeMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MangaCover(manga: Manga, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { MangaCoverImageView(it) },
        onReset = null,
        onRelease = { it.releaseCover() },
        update = { it.bindCover(manga) },
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(homePanelRaised),
    )
}

@Composable
private fun HomeEmptyHint(text: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(homePanel).padding(14.dp),
    ) {
        Text(text, color = homeMuted, fontSize = 12.sp)
    }
}
