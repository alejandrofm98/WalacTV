@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.example.walactv

import com.example.walactv.local.ChannelEntity
import com.example.walactv.local.ContentCacheManager
import com.example.walactv.local.MovieEntity
import com.example.walactv.local.PagedContentLoader
import com.example.walactv.local.SeriesEntity
import com.example.walactv.local.toCatalogItem
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Event
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.compose.foundation.layout.Spacer
import com.bumptech.glide.Glide
import com.example.walactv.ui.theme.IptvAccent
import com.example.walactv.ui.theme.IptvBackground
import com.example.walactv.ui.theme.IptvCard
import com.example.walactv.ui.theme.IptvFocusBg
import com.example.walactv.ui.theme.IptvFocusBorder
import com.example.walactv.ui.theme.IptvLive
import com.example.walactv.ui.theme.IptvOnline
import com.example.walactv.ui.theme.IptvSurface
import com.example.walactv.ui.theme.IptvSurfaceVariant
import com.example.walactv.ui.theme.IptvTextMuted
import com.example.walactv.ui.theme.IptvTextPrimary
import com.example.walactv.ui.theme.IptvTextSecondary
import com.example.walactv.ui.theme.WalacTVTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.walactv.anime.AnimeFlvApiClient
import com.example.walactv.anime.AnimeMedia
import com.example.walactv.anime.AnimeOnAir
import com.example.walactv.anime.AnimeDetail
import com.example.walactv.anime.AnimeEpisodeEntry
import com.example.walactv.anime.AnimeServer
import com.example.walactv.anime.VideoExtractorApiClient
import com.example.walactv.anime.VideoExtractResult
import com.example.walactv.anime.LatestEpisode

class ComposeMainFragment : Fragment() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: IptvRepository
    private lateinit var appUpdateRepository: AppUpdateRepository
    private lateinit var channelStateStore: ChannelStateStore
    private lateinit var watchProgressRepo: WatchProgressRepository
    private lateinit var contentCacheManager: ContentCacheManager

    var composeDialogOpen by mutableStateOf(false)
        private set

    private var homeCatalog by mutableStateOf<HomeCatalog?>(null)
    private var homeSections by mutableStateOf<List<BrowseSection>>(emptyList())
    private var continueWatchingSection by mutableStateOf<BrowseSection?>(null)
    private var continueWatchingEntries by mutableStateOf<Map<String, WatchProgressItem>>(emptyMap())
    private var deleteContinueWatchingItem by mutableStateOf<CatalogItem?>(null)
    private var longPressDialogShown by mutableStateOf(false)
    private var searchableItems by mutableStateOf<List<CatalogItem>>(emptyList())
    private var channelLineup by mutableStateOf<List<CatalogItem>>(emptyList())
    private var channelFilters by mutableStateOf(CatalogFilters())
    private var movieFilters by mutableStateOf(CatalogFilters())
    private var seriesFilters by mutableStateOf(CatalogFilters())
    private var channelFilterCountry by mutableStateOf<String?>(null)
    private var movieFilterCountry by mutableStateOf<String?>(null)
    private var seriesFilterCountry by mutableStateOf<String?>(null)
    private var selectedHero by mutableStateOf<CatalogItem?>(null)
    private var currentMode by mutableStateOf(MainMode.Home)
    private var isRailExpanded by mutableStateOf(false)
    private var isSignedIn by mutableStateOf(false)
    private var loginUsername by mutableStateOf("")
    private var loginPassword by mutableStateOf("")
    private var loginError by mutableStateOf<String?>(null)
    private var isSigningIn by mutableStateOf(false)

    private var isLoaded by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var installedAppVersion by mutableStateOf<InstalledAppVersion?>(null)
    private var availableUpdate by mutableStateOf<AppUpdateInfo?>(null)
    private var mandatoryUpdate by mutableStateOf<AppUpdateInfo?>(null)
    private var updateStatusMessage by mutableStateOf("No comprobado")
    private var updateErrorMessage by mutableStateOf<String?>(null)
    private var isCheckingUpdates by mutableStateOf(false)
    private var isUpdateDownloading by mutableStateOf(false)
    private var pendingInstallPermission by mutableStateOf(false)

    enum class ContentSyncState { IDLE, CHECKING, SYNCING, READY, ERROR }
    private var contentSyncState by mutableStateOf(ContentSyncState.IDLE)
    private var contentSyncError by mutableStateOf<String?>(null)
    private var currentSyncLabel by mutableStateOf("")
    private var currentSyncCount by mutableStateOf(0)
    private var overallSyncProgress by mutableStateOf(0f)

    private var currentItem: CatalogItem? = null
    private var currentStreamIndex: Int = 0
    private var activePlaybackLineup: List<CatalogItem> = emptyList()
    private var playbackReturnState: PlaybackReturnState? = null
    private var pendingUpdateDownloadId: Long? = null
    private var guideInitialGroup: String? = null
    private var continueWatchingRequestVersion: Int = 0

    private var showChannelPicker by mutableStateOf(false)
    private var channelPickerCountry by mutableStateOf(ALL_OPTION)
    private var channelPickerGroup by mutableStateOf(ALL_OPTION)
    private var channelPickerQuery by mutableStateOf("")
    private var channelPickerShowFavorites by mutableStateOf(false)

    private val updateDownloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId == -1L || downloadId != pendingUpdateDownloadId) return
            handleCompletedUpdateDownload(downloadId)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        repository = IptvRepository(requireContext())
        appUpdateRepository = AppUpdateRepository(requireContext())
        channelStateStore = ChannelStateStore(requireContext())
        watchProgressRepo = WatchProgressRepository(requireContext())
        contentCacheManager = ContentCacheManager(requireContext())
        installedAppVersion = appUpdateRepository.installedVersion()
        isSignedIn = repository.hasStoredCredentials()
        loginUsername = repository.currentUsername()

        return ComposeView(requireContext()).apply {
            setContent {
                WalacTVTheme {
                    ComposeRoot()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        restoreCachedUpdateState()
        checkForAppUpdates()
        if (isSignedIn) {
            startLoad()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().registerReceiver(updateDownloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            requireContext().registerReceiver(updateDownloadReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingInstallPermission && canRequestPackageInstalls()) {
            pendingInstallPermission = false
            startUpdateDownload(mandatoryUpdate ?: availableUpdate)
        }
    }

    override fun onStop() {
        runCatching { requireContext().unregisterReceiver(updateDownloadReceiver) }
        super.onStop()
    }

    private fun startLoad(forceRefresh: Boolean = false) {
        if (homeCatalog != null && !forceRefresh) return

        scope.launch {
            errorMessage = null
            contentSyncState = ContentSyncState.CHECKING
            Log.d(TAG, "startLoad: beginning content sync check (forceRefresh=$forceRefresh, homeCatalogExists=${homeCatalog != null})")

            val token = runCatching { repository.getAccessToken() }.getOrNull() ?: ""
            Log.d(TAG, "startLoad: token available=${token.isNotEmpty()}, length=${token.length}")

            val needsChannels = contentCacheManager.needsSyncChannels(token)
            val needsMovies = contentCacheManager.needsSyncMovies(token)
            val needsSeries = contentCacheManager.needsSyncSeries(token)
            Log.d(TAG, "startLoad: needsSync channels=$needsChannels movies=$needsMovies series=$needsSeries")

            val localChannelCount = contentCacheManager.getChannelsCount()
            val localMovieCount = contentCacheManager.getMoviesCount()
            val localSeriesCount = contentCacheManager.getSeriesCount()
            Log.d(TAG, "startLoad: local DB counts channels=$localChannelCount movies=$localMovieCount series=$localSeriesCount")

            if (!needsChannels && !needsMovies && !needsSeries) {
                contentSyncState = ContentSyncState.READY
                Log.d(TAG, "startLoad: cache up to date, loading local filters")
                loadLocalFilters()
            } else {
                contentSyncState = ContentSyncState.SYNCING
                currentSyncLabel = ""
                currentSyncCount = 0
                overallSyncProgress = 0f

                val results = mutableListOf<Result<*>>()
                val totalSteps = listOf(needsChannels, needsMovies, needsSeries).count { it }.coerceAtLeast(1)
                var completedSteps = 0

                if (needsChannels) {
                    Log.d(TAG, "startLoad: syncing channels from API...")
                    currentSyncLabel = "Sincronizando canales"
                    val r = contentCacheManager.syncChannels(token)
                    results.add(r)
                    completedSteps++
                    overallSyncProgress = completedSteps.toFloat() / totalSteps
                    currentSyncCount = r.getOrNull() as? Int ?: 0
                    Log.d(TAG, "startLoad: channels sync result: ${if (r.isSuccess) "OK (${r.getOrNull()} items)" else "FAIL (${r.exceptionOrNull()?.message})"}")
                } else {
                    completedSteps++
                    overallSyncProgress = completedSteps.toFloat() / totalSteps
                    Log.d(TAG, "startLoad: channels already up to date")
                }

                if (needsMovies) {
                    Log.d(TAG, "startLoad: syncing movies from API...")
                    currentSyncLabel = "Sincronizando películas"
                    val r = contentCacheManager.syncMovies(token)
                    results.add(r)
                    completedSteps++
                    overallSyncProgress = completedSteps.toFloat() / totalSteps
                    currentSyncCount = r.getOrNull() as? Int ?: 0
                    Log.d(TAG, "startLoad: movies sync result: ${if (r.isSuccess) "OK (${r.getOrNull()} items)" else "FAIL (${r.exceptionOrNull()?.message})"}")
                } else {
                    completedSteps++
                    overallSyncProgress = completedSteps.toFloat() / totalSteps
                    Log.d(TAG, "startLoad: movies already up to date")
                }

                if (needsSeries) {
                    Log.d(TAG, "startLoad: syncing series from API...")
                    currentSyncLabel = "Sincronizando series"
                    val r = contentCacheManager.syncSeries(token)
                    results.add(r)
                    completedSteps++
                    overallSyncProgress = completedSteps.toFloat() / totalSteps
                    currentSyncCount = r.getOrNull() as? Int ?: 0
                    Log.d(TAG, "startLoad: series sync result: ${if (r.isSuccess) "OK (${r.getOrNull()} items)" else "FAIL (${r.exceptionOrNull()?.message})"}")
                } else {
                    completedSteps++
                    overallSyncProgress = completedSteps.toFloat() / totalSteps
                    Log.d(TAG, "startLoad: series already up to date")
                }

                val hasError = results.any { it.isFailure }
                if (hasError) {
                    contentSyncState = ContentSyncState.ERROR
                    contentSyncError = "Error al sincronizar contenido"
                    Log.e(TAG, "startLoad: sync completed with errors")
                } else {
                    currentSyncLabel = "Sincronización completada"
                    currentSyncCount = 0
                    overallSyncProgress = 1f
                    contentSyncState = ContentSyncState.READY
                    Log.d(TAG, "startLoad: all syncs completed successfully, loading local filters")
                    loadLocalFilters()
                }
            }

            val finalChannelCount = contentCacheManager.getChannelsCount()
            val finalMovieCount = contentCacheManager.getMoviesCount()
            val finalSeriesCount = contentCacheManager.getSeriesCount()
            Log.d(TAG, "startLoad: final DB counts channels=$finalChannelCount movies=$finalMovieCount series=$finalSeriesCount")

            // Ahora cargar el home catalog
            Log.d(TAG, "startLoad: loading home catalog")
            runCatching { repository.loadHomeCatalog(forceRefresh = forceRefresh) }
                .onSuccess { catalog ->
                    homeCatalog = catalog
                    updateStateFromCatalog(catalog)
                    isLoaded = true
                    Log.d(TAG, "startLoad: home catalog loaded, isLoaded=true")
                }
                .onFailure {
                    if (!isLoaded) {
                        errorMessage = it.message ?: "Error al cargar la aplicacion"
                        Log.e(TAG, "startLoad: home catalog load failed", it)
                    }
                }
        }
    }

    private fun loadLocalFilters() {
        scope.launch {
            try {
                channelFilters = contentCacheManager.getLocalChannelFilters()
                Log.d(TAG, "loadLocalFilters: channelFilters countries=${channelFilters.countries.size} groups=${channelFilters.groups.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading local channel filters", e)
            }
            try {
                movieFilters = contentCacheManager.getLocalMovieFilters()
                Log.d(TAG, "loadLocalFilters: movieFilters countries=${movieFilters.countries.size} groups=${movieFilters.groups.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading local movie filters", e)
            }
            try {
                seriesFilters = contentCacheManager.getLocalSeriesFilters()
                Log.d(TAG, "loadLocalFilters: seriesFilters countries=${seriesFilters.countries.size} groups=${seriesFilters.groups.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading local series filters", e)
            }
        }
    }

    private fun updateStateFromCatalog(catalog: HomeCatalog) {
        catalog.favoriteItems?.let { favorites ->
            channelStateStore.replaceFavoriteIds(favorites.map(CatalogItem::stableId))
        }
        searchableItems = catalog.searchableItems
        CatalogMemory.searchableItems = searchableItems
        channelLineup = searchableItems.filter { it.kind == ContentKind.CHANNEL }
        rebuildHomeSections()

        if (selectedHero == null || searchableItems.none { it.stableId == selectedHero?.stableId }) {
            selectedHero = defaultItemForMode(currentMode)
        }

        loadContinueWatching()
    }

    private fun rebuildHomeSections() {
        val baseSections = buildDisplaySections(homeCatalog?.sections.orEmpty(), searchableItems)
        homeSections = continueWatchingSection?.let { cw ->
            if (baseSections.isEmpty()) {
                listOf(cw)
            } else {
                listOf(baseSections.first()) + cw + baseSections.drop(1)
            }
        } ?: baseSections
        Log.d(
            TAG,
            "rebuildHomeSections: cw=${continueWatchingSection?.items?.size ?: 0} base=${baseSections.size} total=${homeSections.size} mode=$currentMode favoriteIds=${channelStateStore.favoriteIds().size} favoriteItems=${homeCatalog?.favoriteItems?.size}",
        )
    }

    private fun loadContinueWatching() {
        val requestVersion = ++continueWatchingRequestVersion
        val searchableSnapshot = searchableItems
        scope.launch {
            try {
                val items = watchProgressRepo.getContinueWatching()
                Log.d(TAG, "Continue watching[$requestVersion]: API returned ${items.size} items, searchable=${searchableSnapshot.size}")
                if (items.isNotEmpty()) {
                    val entryMap = mutableMapOf<String, WatchProgressItem>()
                    val catalogItems = items.map { wp ->
                        Log.d(TAG, "CW[$requestVersion] item: id=${wp.contentId} type=${wp.contentType} series=${wp.seriesName} progress=${wp.progressPercent}%")
                        val synthetic = buildContinueWatchingItem(wp, searchableSnapshot)
                        entryMap[synthetic.stableId] = wp
                        synthetic
                    }
                    Log.d(TAG, "Continue watching[$requestVersion]: built ${catalogItems.size} synthetic items from ${items.size} API items")
                    if (requestVersion != continueWatchingRequestVersion) {
                        Log.d(TAG, "Continue watching[$requestVersion]: stale result ignored")
                        return@launch
                    }
                    continueWatchingEntries = entryMap
                    continueWatchingSection = catalogItems.takeIf { it.isNotEmpty() }
                        ?.let { BrowseSection("Continuar viendo", it) }
                    rebuildHomeSections()
                } else {
                    if (requestVersion != continueWatchingRequestVersion) {
                        Log.d(TAG, "Continue watching[$requestVersion]: empty stale result ignored")
                        return@launch
                    }
                    continueWatchingEntries = emptyMap()
                    continueWatchingSection = null
                    rebuildHomeSections()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load continue watching[$requestVersion]: ${e.message}", e)
            }
        }
    }

    private suspend fun deleteAllSeriesProgress(seriesName: String) {
        try {
            val entriesToDelete = continueWatchingEntries.filter { (_, wp) ->
                wp.seriesName == seriesName
            }
            Log.d(TAG, "Deleting ${entriesToDelete.size} episodes for series: $seriesName")
            entriesToDelete.forEach { (stableId, wp) ->
                watchProgressRepo.deleteProgress(wp.contentId)
                Log.d(TAG, "Deleted progress for episode: ${wp.contentId}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting series progress for $seriesName", e)
        }
    }

    private fun buildContinueWatchingItem(wp: WatchProgressItem, searchableSnapshot: List<CatalogItem>): CatalogItem {
        val kind = if (wp.contentType == "series") ContentKind.SERIES else ContentKind.MOVIE
        val subtitle = if (wp.contentType == "series") {
            buildEpisodeLabel(wp.seasonNumber, wp.episodeNumber)
        } else {
            ""
        }
        val fallbackTitle = wp.normalizedTitle.cleanDisplayText()
            .ifBlank { wp.seriesName.cleanDisplayText() }
            .ifBlank { wp.title.cleanDisplayText() }
        val fallbackDescription = wp.title.ifBlank { fallbackTitle }
        val fallbackImageUrl = wp.imageUrl
        val fallbackStableId = if (wp.contentType == "series") {
            "cw_series:${wp.contentId}"
        } else {
            "cw_movie:${wp.contentId}"
        }

        val matched = when (wp.contentType) {
            "movie" -> searchableSnapshot.firstOrNull { item ->
                item.kind == ContentKind.MOVIE && item.matchesByProviderId(wp.contentId)
            }
            "series" -> findSeriesMatch(wp, searchableSnapshot)
            else -> null
        }

        return matched?.copy(
            stableId = fallbackStableId,
            providerId = wp.contentId,
            title = fallbackTitle,
            normalizedTitle = null,
            subtitle = subtitle,
            description = matched.description.cleanDisplayText().ifBlank { fallbackDescription },
            imageUrl = matched.imageUrl.ifBlank { fallbackImageUrl },
            seriesName = matched.seriesName.cleanDisplayText().ifBlank { wp.seriesName.orEmpty() }.ifBlank { null },
        ) ?: CatalogItem(
            stableId = fallbackStableId,
            providerId = wp.contentId,
            title = fallbackTitle,
            normalizedTitle = null,
            subtitle = subtitle,
            description = fallbackDescription,
            imageUrl = fallbackImageUrl,
            kind = kind,
            group = "Continuar viendo",
            badgeText = when (kind) {
                ContentKind.MOVIE -> "Pelicula"
                ContentKind.SERIES -> "Serie"
                else -> ""
            },
            seriesName = wp.seriesName,
            seasonNumber = wp.seasonNumber,
            episodeNumber = wp.episodeNumber,
            streamOptions = emptyList(),
        )
    }

    private fun String?.cleanDisplayText(): String {
        return this
            ?.takeUnless { it.equals("null", ignoreCase = true) }
            ?.trim()
            .orEmpty()
    }

    private fun CatalogItem.matchesByProviderId(contentId: String): Boolean {
        val itemId = contentId.substringAfterLast(":")
        return providerId == itemId || stableId == contentId || stableId.endsWith(":$itemId")
    }

    private fun findSeriesMatch(wp: WatchProgressItem, items: List<CatalogItem> = searchableItems): CatalogItem? {
        val seriesName = wp.seriesName ?: return null
        val seriesItems = items.filter { it.kind == ContentKind.SERIES }

        seriesItems.firstOrNull { it.seriesName == seriesName }?.let { return it }
        seriesItems.firstOrNull { it.seriesName?.equals(seriesName, ignoreCase = true) == true }?.let { return it }

        val baseName = seriesName.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ").trim()
        seriesItems.firstOrNull { item ->
            val itemBase = item.seriesName?.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ")?.trim()
            itemBase.equals(baseName, ignoreCase = true)
        }?.let { return it }

        seriesItems.firstOrNull { item ->
            val ns = item.seriesName ?: return@firstOrNull false
            ns.contains(seriesName, ignoreCase = true) || seriesName.contains(ns, ignoreCase = true)
        }?.let { return it }

        seriesItems.firstOrNull { item ->
            item.title.contains(seriesName, ignoreCase = true) || seriesName.contains(item.title, ignoreCase = true)
        }?.let { return it }

        Log.w(TAG, "No series match found for: '$seriesName'")
        return null
    }

    private fun buildEpisodeLabel(season: Int?, episode: Int?): String {
        val s = season?.let { "S%02d".format(it) } ?: ""
        val e = episode?.let { "E%02d".format(it) } ?: ""
        return s + e
    }

    private fun restoreCachedUpdateState() {
        val installed = installedAppVersion ?: return
        val cachedUpdate = appUpdateRepository.loadCachedUpdate() ?: return
        availableUpdate = cachedUpdate
        when (evaluateAppUpdate(installed, cachedUpdate)) {
            AppUpdateAvailability.REQUIRED -> {
                mandatoryUpdate = cachedUpdate
                updateStatusMessage = "Actualizacion obligatoria disponible"
            }
            AppUpdateAvailability.OPTIONAL -> {
                mandatoryUpdate = null
                updateStatusMessage = "Nueva version ${cachedUpdate.latestVersionName} disponible"
            }
            AppUpdateAvailability.UP_TO_DATE -> {
                mandatoryUpdate = null
                updateStatusMessage = "Aplicacion actualizada"
            }
        }
    }

    private fun checkForAppUpdates(showToast: Boolean = false) {
        scope.launch {
            isCheckingUpdates = true
            updateErrorMessage = null

            val result = runCatching { appUpdateRepository.fetchRemoteUpdate() }
            if (result.isFailure) {
                Log.e(TAG, "Error al comprobar actualizaciones", result.exceptionOrNull())
            }
            val remoteUpdate = result.getOrNull()
            if (remoteUpdate == null) {
                if (mandatoryUpdate != null) {
                    updateStatusMessage = "Actualizacion obligatoria pendiente"
                } else {
                    updateStatusMessage = "No se pudo comprobar"
                    if (showToast) {
                        Toast.makeText(requireContext(), "No se pudo comprobar: ${result.exceptionOrNull()?.message ?: "sin conexion"}", Toast.LENGTH_LONG).show()
                    }
                }
                isCheckingUpdates = false
                return@launch
            }

            appUpdateRepository.cacheUpdate(remoteUpdate)
            availableUpdate = remoteUpdate

            when (evaluateAppUpdate(installedAppVersion ?: return@launch, remoteUpdate)) {
                AppUpdateAvailability.REQUIRED -> {
                    mandatoryUpdate = remoteUpdate
                    updateStatusMessage = "Actualizacion obligatoria disponible"
                }
                AppUpdateAvailability.OPTIONAL -> {
                    mandatoryUpdate = null
                    updateStatusMessage = "Nueva version ${remoteUpdate.latestVersionName} disponible"
                }
                AppUpdateAvailability.UP_TO_DATE -> {
                    mandatoryUpdate = null
                    updateStatusMessage = "Aplicacion actualizada"
                }
            }

            if (showToast) {
                Toast.makeText(requireContext(), updateStatusMessage, Toast.LENGTH_SHORT).show()
            }
            isCheckingUpdates = false
        }
    }

    private fun startUpdateFlow() {
        val updateInfo = mandatoryUpdate ?: availableUpdate ?: return
        if (!canRequestPackageInstalls()) {
            pendingInstallPermission = true
            updateStatusMessage = "Permite instalar apps desconocidas para continuar"
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${requireContext().packageName}"),
            )
            val canOpenSettings = intent.resolveActivity(requireContext().packageManager) != null
            if (canOpenSettings) {
                runCatching { startActivity(intent) }
                    .onFailure { updateErrorMessage = "No se pudo abrir la configuracion de instalacion" }
            } else {
                updateErrorMessage = "Activa manualmente la instalacion desde origenes desconocidos para WalacTV"
            }
            return
        }
        startUpdateDownload(updateInfo)
    }

    private fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                requireContext().packageManager.canRequestPackageInstalls()

    private fun startUpdateDownload(updateInfo: AppUpdateInfo?) {
        val info = updateInfo ?: return
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("WalacTV ${info.latestVersionName}")
            .setDescription("Descargando actualizacion")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                requireContext(),
                Environment.DIRECTORY_DOWNLOADS,
                "WalacTV-${info.latestVersionName}.apk",
            )

        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        pendingUpdateDownloadId = downloadManager.enqueue(request)
        isUpdateDownloading = true
        updateStatusMessage = "Descargando actualizacion ${info.latestVersionName}"
        Toast.makeText(requireContext(), updateStatusMessage, Toast.LENGTH_SHORT).show()
    }

    private fun handleCompletedUpdateDownload(downloadId: Long) {
        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    isUpdateDownloading = false
                    pendingUpdateDownloadId = null
                    val uri = downloadManager.getUriForDownloadedFile(downloadId)
                    if (uri != null) launchApkInstaller(uri)
                    else updateErrorMessage = "La descarga termino pero no se pudo abrir el APK"
                }
                DownloadManager.STATUS_FAILED -> {
                    isUpdateDownloading = false
                    pendingUpdateDownloadId = null
                    updateErrorMessage = "No se pudo descargar la actualizacion"
                    updateStatusMessage = "Error al descargar la actualizacion"
                }
            }
        }
    }

    private fun launchApkInstaller(apkUri: Uri) {
        val contentUri = try {
            val file = java.io.File(apkUri.path ?: return)
            androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file,
            )
        } catch (_: Exception) {
            apkUri
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure { updateErrorMessage = "No se pudo abrir el instalador" }
    }

    // ── Compose root ──────────────────────────────────────────────────────────

    @Composable
    private fun ComposeRoot() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(IptvBackground),
        ) {
            when {
                mandatoryUpdate != null -> MandatoryUpdateScreen(mandatoryUpdate!!)
                !isSignedIn -> LoginScreen()
                errorMessage != null -> ErrorScreen(errorMessage.orEmpty())
                contentSyncState == ContentSyncState.SYNCING || contentSyncState == ContentSyncState.CHECKING -> SyncScreen()
                !isLoaded -> LoadingScreen()
                else -> MainShell()
            }
        }
    }

    @Composable
    private fun SyncScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(IptvBackground)
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedSyncSpinner(overallSyncProgress)

            Spacer(modifier = Modifier.height(32.dp))

            if (contentSyncState == ContentSyncState.CHECKING) {
                Text(
                    "Comprobando actualizaciones...",
                    color = IptvTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    currentSyncLabel,
                    color = IptvTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )

                if (currentSyncCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${currentSyncCount.toLocaleString()} elementos",
                        color = IptvTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            if (contentSyncError != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    contentSyncError!!,
                    color = IptvLive,
                    fontSize = 14.sp
                )
            }
        }
    }

    @Composable
    private fun AnimatedSyncSpinner(progress: Float) {
        val infiniteTransition = rememberInfiniteTransition(label = "syncSpinner")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        val animatedProgress by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(300),
            label = "progress"
        )

        Box(
            modifier = Modifier
                .size(64.dp)
                .rotate(rotation),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 4.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val sweepAngle = animatedProgress * 360f
                drawArc(
                    color = IptvSurface,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth)
                )
                if (sweepAngle > 1f) {
                    drawArc(
                        color = IptvAccent,
                        startAngle = -90f,
                        sweepAngle = sweepAngle.coerceAtMost(360f),
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth)
                    )
                }
            }
        }
    }

    private fun Int.toLocaleString(): String {
        return this.toString().reversed().chunked(3).joinToString(".").reversed()
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Composable
    private fun LoginScreen() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .background(IptvSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(10.dp))
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Iniciar sesion", color = IptvTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Introduce tu usuario y contraseña para cargar los canales.",
                    color = IptvTextMuted,
                    fontSize = 16.sp,
                )
                LoginField(value = loginUsername, label = "Usuario", hidden = false) { loginUsername = it }
                LoginField(value = loginPassword, label = "Contraseña", hidden = true) { loginPassword = it }
                loginError?.let { Text(it, color = IptvLive, fontSize = 14.sp) }
                FocusButton(label = if (isSigningIn) "Entrando..." else "Entrar", icon = Icons.Outlined.PlayArrow) {
                    if (!isSigningIn) performSignIn()
                }
            }
        }
    }

    @Composable
    private fun LoginField(value: String, label: String, hidden: Boolean, onValueChange: (String) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = IptvTextMuted, fontSize = 14.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IptvCard, RoundedCornerShape(8.dp))
                    .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
                        Text(
                            if (hidden) "Escribe tu contraseña" else "Escribe tu usuario",
                            color = IptvTextMuted,
                            fontSize = 16.sp,
                        )
                    }
                    innerTextField()
                },
            )
        }
    }

    private fun performSignIn() {
        loginError = null
        isSigningIn = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.signIn(loginUsername, loginPassword) } }
                .onSuccess {
                    resetCatalogState()
                    isSignedIn = true
                    isSigningIn = false
                    startLoad()
                }
                .onFailure {
                    isSigningIn = false
                    Log.e(TAG, "Error al iniciar sesion", it)
                    loginError = it.message ?: "No se pudo iniciar sesion"
                }
        }
    }

    private fun performSignOut() {
        repository.signOut()
        resetCatalogState()
        isSignedIn = false
        loginUsername = ""
        loginPassword = ""
        loginError = null
    }

    private fun resetCatalogState() {
        Log.d(TAG, "resetCatalogState called - setting currentMode=Home")
        homeCatalog = null
        homeSections = emptyList()
        continueWatchingSection = null
        continueWatchingEntries = emptyMap()
        searchableItems = emptyList()
        channelLineup = emptyList()
        selectedHero = null
        isLoaded = false
        errorMessage = null
        currentItem = null
        currentStreamIndex = 0
        activePlaybackLineup = emptyList()
        currentMode = MainMode.Home
        channelFilters = CatalogFilters()
        movieFilters = CatalogFilters()
        seriesFilters = CatalogFilters()
        channelFilterCountry = null
        movieFilterCountry = null
        seriesFilterCountry = null
        contentSyncState = ContentSyncState.IDLE
        contentSyncError = null
        currentSyncLabel = ""
        currentSyncCount = 0
        overallSyncProgress = 0f
    }

    // ── Loading / Error ───────────────────────────────────────────────────────

    @Composable
    private fun LoadingScreen() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.width(520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("WalacTV", color = IptvTextPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text("Cargando contenido...", color = IptvTextMuted, fontSize = 18.sp)
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(IptvSurfaceVariant, RoundedCornerShape(6.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.04f)
                            .height(10.dp)
                            .background(IptvAccent, RoundedCornerShape(6.dp)),
                    )
                }
            }
        }
    }

    @Composable
    private fun ErrorScreen(message: String) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(620.dp)
                    .background(IptvSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(10.dp))
                    .padding(28.dp),
            ) {
                Text("No se pudo cargar WalacTV", color = IptvTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(message, color = IptvTextMuted, fontSize = 18.sp)
                Spacer(Modifier.height(24.dp))
                FocusButton(label = "Reintentar", icon = Icons.Outlined.PlayArrow) { startLoad() }
            }
        }
    }

    // ── Mandatory update ──────────────────────────────────────────────────────

    @Composable
    private fun MandatoryUpdateScreen(updateInfo: AppUpdateInfo) {
        val installed = installedAppVersion
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(700.dp)
                    .background(IptvSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, IptvFocusBorder, RoundedCornerShape(12.dp))
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("Actualizacion obligatoria", color = IptvTextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Debes instalar la nueva version para seguir usando WalacTV.", color = IptvTextMuted, fontSize = 18.sp)
                installed?.let { SettingsRow("Version instalada", "${it.versionName} (${it.versionCode})") }
                SettingsRow("Version requerida", "${updateInfo.latestVersionName} (${updateInfo.latestVersionCode})")
                if (updateInfo.changelog.isNotBlank()) Text(updateInfo.changelog, color = IptvTextPrimary, fontSize = 16.sp)
                Text(updateStatusMessage, color = IptvAccent, fontSize = 15.sp)
                updateErrorMessage?.let { Text(it, color = IptvLive, fontSize = 14.sp) }
                if (isUpdateDownloading) Text("La descarga esta en curso. Al terminar se abrira el instalador.", color = IptvTextMuted, fontSize = 14.sp)
                FocusButton(label = if (isUpdateDownloading) "Descargando..." else "Descargar actualizacion", icon = Icons.Outlined.PlayArrow) {
                    if (!isUpdateDownloading) startUpdateFlow()
                }
                FocusButton(label = if (isCheckingUpdates) "Comprobando..." else "Reintentar", icon = Icons.Outlined.History) {
                    if (!isCheckingUpdates) checkForAppUpdates(showToast = true)
                }
            }
        }
    }

    // ── Main shell ────────────────────────────────────────────────────────────

    @Composable
    private fun MainShell() {
        Log.d(TAG, "MainShell: currentMode=$currentMode")
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                SideRail()
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (currentMode) {
                        MainMode.Home     -> HomeContent()
                        MainMode.TV       -> GuideContent(ContentKind.CHANNEL)
                        MainMode.Events   -> GuideContent(ContentKind.EVENT)
                        MainMode.Movies   -> VodGridContent(ContentKind.MOVIE)
                        MainMode.Series   -> VodGridContent(ContentKind.SERIES)
                        MainMode.Settings -> SettingsContent()
                        MainMode.Anime    -> AnimeBrowseContent()
                    }
                }
            }
            if (showChannelPicker) {
                Dialog(
                    onDismissRequest = { showChannelPicker = false },
                    properties = DialogProperties(
                        dismissOnBackPress = true,
                        dismissOnClickOutside = false,
                        usePlatformDefaultWidth = false
                    )
                ) {
                    ChannelPickerDialog(
                        currentCountry = channelPickerCountry,
                        currentGroup = channelPickerGroup,
                        searchQuery = channelPickerQuery,
                        showFavorites = channelPickerShowFavorites,
                        onCountryChange = { channelPickerCountry = it },
                        onGroupChange = { channelPickerGroup = it },
                        onFavoritesChange = { channelPickerShowFavorites = it },
                        onSearchChange = { channelPickerQuery = it },
                        onChannelSelected = { item ->
                            playCatalogItem(item, 0)
                            showChannelPicker = false
                        },
                        onDismiss = { showChannelPicker = false }
                    )
                }
            }
        }
    }

    // ── Side rail ─────────────────────────────────────────────────────────────

    @Composable
    private fun SideRail() {
        val railItems = buildDefaultSideRailEntries().map(::toNavItem)
        val focusRequesters = remember { List(railItems.size + 1) { FocusRequester() } }

        val railWidth by animateDpAsState(
            targetValue = if (isRailExpanded) 248.dp else 78.dp,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "railWidthAnim",
        )

        Column(
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(IptvSurface)
                .border(1.dp, IptvSurfaceVariant)
                .focusable()
                .onFocusChanged { isRailExpanded = it.hasFocus }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                        keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    ) {
                        val index = railItems.indexOfFirst { it.mode != null && currentMode == it.mode }
                        if (index >= 0 && index < focusRequesters.size) runCatching { focusRequesters[index].requestFocus() }
                        else if (focusRequesters.isNotEmpty()) runCatching { focusRequesters.last().requestFocus() }
                        true
                    } else false
                },
        ) {
            Box(modifier = Modifier.height(80.dp)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isRailExpanded,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(150)),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text("WalacTV", color = IptvTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text("Navegacion", color = IptvTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                railItems.forEachIndexed { index, item ->
                    NavigationItem(
                        icon = item.icon,
                        label = item.label,
                        selected = item.mode != null && currentMode == item.mode,
                        expanded = isRailExpanded,
                        modifier = Modifier.focusRequester(focusRequesters[index]),
                    ) { item.onClick?.invoke() ?: item.mode?.let(::changeMode) }
                }
            }

            Box(modifier = Modifier.padding(6.dp)) {
                NavigationItem(
                    icon = Icons.Outlined.Settings,
                    label = "Ajustes",
                    selected = currentMode == MainMode.Settings,
                    expanded = isRailExpanded,
                    modifier = Modifier.focusRequester(focusRequesters.last()),
                ) { changeMode(MainMode.Settings) }
            }
        }
    }

    private fun changeMode(newMode: MainMode) {
        if (currentMode == newMode) {
            Log.d(TAG, "changeMode: SAME mode $newMode, no-op")
            return
        }
        Log.d(TAG, "changeMode: $currentMode -> $newMode")
        currentMode = newMode
        selectedHero = defaultItemForMode(newMode)
        when (newMode) {
            MainMode.TV     -> ensureFiltersLoaded(ContentKind.CHANNEL)
            MainMode.Movies -> ensureFiltersLoaded(ContentKind.MOVIE)
            MainMode.Series -> ensureFiltersLoaded(ContentKind.SERIES)
            else            -> Unit
        }
    }

    private fun rememberPlaybackReturnState(item: CatalogItem) {
        playbackReturnState = PlaybackReturnState(
            mode = currentMode,
            selectedItemStableId = item.stableId,
        )
        Log.d(TAG, "Saved playback return state: mode=${currentMode}, item=${item.stableId}")
    }

    fun restorePlaybackReturnState() {
        val state = playbackReturnState ?: return
        playbackReturnState = null

        Log.d(TAG, "Restoring playback return state: mode=${state.mode} (was $currentMode)")

        currentMode = state.mode
        when (state.mode) {
            MainMode.TV     -> ensureFiltersLoaded(ContentKind.CHANNEL)
            MainMode.Movies -> ensureFiltersLoaded(ContentKind.MOVIE)
            MainMode.Series -> ensureFiltersLoaded(ContentKind.SERIES)
            else            -> Unit
        }

        selectedHero = searchableItems.firstOrNull { it.stableId == state.selectedItemStableId }
            ?: defaultItemForMode(currentMode)
    }

    fun restoreFocusAfterPlayer() {
        view?.let {
            runCatching { it.requestFocus() }
            Log.d(TAG, "Requested focus on fragment view after player close")
        }
    }

    // ── Métodos públicos de navegación (usados por MainActivity) ─────────────

    /**
     * Devuelve el nombre del modo actual para que MainActivity pueda decidir
     * cómo gestionar el back sin necesidad de acceder a la enum interna.
     */
    fun currentNavigationMode(): String = currentMode.name

    /**
     * Navega explícitamente a Home. Usado por MainActivity cuando el usuario
     * pulsa back desde cualquier sección que no sea Home.
     */
    fun navigateToHome() {
        Log.d(TAG, "navigateToHome called, currentMode=$currentMode")
        if (currentMode == MainMode.Home) return
        changeMode(MainMode.Home)
    }

    /**
     * Mantenido por compatibilidad. Preferir currentNavigationMode() + navigateToHome().
     */
    fun navigateHomeOnBack(): Boolean {
        Log.d(TAG, "navigateHomeOnBack called, currentMode=$currentMode")
        if (currentMode == MainMode.Home) {
            Log.d(TAG, "navigateHomeOnBack: already Home, returning false")
            return false
        }
        Log.d(TAG, "navigateHomeOnBack: changing to Home")
        changeMode(MainMode.Home)
        return true
    }

    fun navigateHomeOnBack_unused(): Boolean {
        // Alias legacy — no usar
        return navigateHomeOnBack()
    }

    fun restoreFocusAfterPlayer_unused() {
        // Alias legacy — no usar
        restoreFocusAfterPlayer()
    }

    private fun ensureFiltersLoaded(kind: ContentKind, country: String? = null) {
        val alreadyLoaded = when (kind) {
            ContentKind.CHANNEL -> channelFilters.countries.isNotEmpty() && channelFilterCountry == country
            ContentKind.MOVIE   -> movieFilters.countries.isNotEmpty() && movieFilterCountry == country
            ContentKind.SERIES  -> seriesFilters.countries.isNotEmpty() && seriesFilterCountry == country
            ContentKind.EVENT   -> true
        }
        Log.d(TAG, "ensureFiltersLoaded: kind=$kind country=$country alreadyLoaded=$alreadyLoaded")
        if (alreadyLoaded) return

        scope.launch {
            runCatching { repository.loadCatalogFilters(kind, country) }
                .onSuccess { filters ->
                    Log.d(TAG, "ensureFiltersLoaded: API returned ${filters.countries.size} countries, ${filters.groups.size} groups for $kind")
                    when (kind) {
                        ContentKind.CHANNEL -> { channelFilters = filters; channelFilterCountry = country }
                        ContentKind.MOVIE   -> { movieFilters = filters; movieFilterCountry = country }
                        ContentKind.SERIES  -> { seriesFilters = filters; seriesFilterCountry = country }
                        ContentKind.EVENT   -> Unit
                    }
                }
                .onFailure { Log.e(TAG, "No se pudieron cargar filtros para $kind", it) }
        }
    }

    private suspend fun ensureFiltersLoadedAwait(kind: ContentKind, country: String? = null) {
        val alreadyLoaded = when (kind) {
            ContentKind.CHANNEL -> channelFilters.countries.isNotEmpty() && channelFilterCountry == country
            ContentKind.MOVIE   -> movieFilters.countries.isNotEmpty() && movieFilterCountry == country
            ContentKind.SERIES  -> seriesFilters.countries.isNotEmpty() && seriesFilterCountry == country
            ContentKind.EVENT   -> true
        }
        if (alreadyLoaded) {
            Log.d(TAG, "ensureFiltersLoadedAwait: already loaded for $kind")
            return
        }
        Log.d(TAG, "ensureFiltersLoadedAwait: loading from API for $kind")
        runCatching {
            withContext(Dispatchers.IO) { repository.loadCatalogFilters(kind, country) }
        }
            .onSuccess { filters ->
                Log.d(TAG, "ensureFiltersLoadedAwait: API returned ${filters.countries.size} countries, ${filters.groups.size} groups")
                when (kind) {
                    ContentKind.CHANNEL -> { channelFilters = filters; channelFilterCountry = country }
                    ContentKind.MOVIE   -> { movieFilters = filters; movieFilterCountry = country }
                    ContentKind.SERIES  -> { seriesFilters = filters; seriesFilterCountry = country }
                    ContentKind.EVENT   -> Unit
                }
            }
            .onFailure { Log.e(TAG, "ensureFiltersLoadedAwait FAILED for $kind", it) }
    }

    private fun openSearch() {
        isRailExpanded = false
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, SearchFragment.newInstance(searchableItems))
            .addToBackStack("SearchFragment")
            .commit()
    }

    private fun toNavItem(entry: SideRailEntry): NavItem {
        return when (entry.destination) {
            SideRailDestination.SEARCH -> NavItem(Icons.Outlined.Search, entry.label, onClick = ::openSearch)
            SideRailDestination.HOME -> NavItem(Icons.Outlined.Home, entry.label, MainMode.Home)
            SideRailDestination.EVENTS -> NavItem(Icons.Outlined.Event, entry.label, MainMode.Events)
            SideRailDestination.TV -> NavItem(Icons.Outlined.LiveTv, entry.label, MainMode.TV)
            SideRailDestination.MOVIES -> NavItem(Icons.Outlined.Movie, entry.label, MainMode.Movies)
            SideRailDestination.SERIES -> NavItem(Icons.Outlined.Tv, entry.label, MainMode.Series)
            SideRailDestination.ANIME -> NavItem(Icons.Outlined.PlayArrow, entry.label, MainMode.Anime)
        }
    }

    private fun defaultItemForMode(mode: MainMode): CatalogItem? = when (mode) {
        MainMode.Home     -> homeSections.firstNotNullOfOrNull { it.items.firstOrNull() }
        MainMode.TV       -> searchableItems.firstOrNull { it.kind == ContentKind.CHANNEL }
        MainMode.Events   -> searchableItems.firstOrNull { it.kind == ContentKind.EVENT }
        MainMode.Movies   -> searchableItems.firstOrNull { it.kind == ContentKind.MOVIE }
        MainMode.Series   -> searchableItems.firstOrNull { it.kind == ContentKind.SERIES }
        MainMode.Settings -> null
        MainMode.Anime    -> null
    }

    // ── Navigation item ───────────────────────────────────────────────────────

    @Composable
    private fun NavigationItem(
        icon: ImageVector,
        label: String,
        selected: Boolean,
        expanded: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        var isFocused by remember { mutableStateOf(false) }
        val bgColor = when { isFocused -> IptvFocusBg; selected -> IptvCard; else -> Color.Transparent }
        val borderColor = when { isFocused -> IptvFocusBorder; selected -> IptvSurfaceVariant; else -> Color.Transparent }
        val contentColor = if (isFocused || selected) IptvTextPrimary else IptvTextMuted

        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(bgColor, RoundedCornerShape(8.dp))
                .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(8.dp))
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { onClick() }
                .padding(horizontal = if (expanded) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(18.dp))
            AnimatedVisibility(visible = expanded, enter = fadeIn(tween(300)), exit = fadeOut(tween(150))) {
                Row {
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    // ── Home ──────────────────────────────────────────────────────────────────

    @Composable
    private fun HomeContent() {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { ScreenHeader(title = "Inicio", subtitle = "") }
            items(homeSections) { section ->
                ContentSection(
                    section = section,
                    onFocused = { selectedHero = it },
                    onLoadMore = if (section.contentType != null && section.groupName != null && section.hasNextPage) { sectionToLoad, onDone ->
                        scope.launch {
                            try {
                                val nextPage = sectionToLoad.currentPage + 1
                                val (newItems, hasNext) = repository.loadContentPage(
                                    sectionToLoad.contentType!!,
                                    sectionToLoad.groupName!!,
                                    nextPage,
                                    12,
                                    sectionToLoad.year
                                )
                                if (newItems.isNotEmpty()) {
                                    val updated = sectionToLoad.copy(
                                        items = sectionToLoad.items + newItems,
                                        currentPage = nextPage,
                                        hasNextPage = hasNext,
                                    )
                                    val index = homeSections.indexOfFirst { it.title == sectionToLoad.title }
                                    if (index >= 0) {
                                        homeSections = homeSections.toMutableList().also { it[index] = updated }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "loadContentPage failed", e)
                            } finally {
                                onDone()
                            }
                        }
                    } else null,
                )
            }
        }

        deleteContinueWatchingItem?.let { item ->
            val isSeries = item.kind == ContentKind.SERIES
            DeleteConfirmationOverlay(
                item = item,
                isSeries = isSeries,
                onDismiss = {
                    Log.d(TAG, "HOME: Delete dialog dismissed")
                    deleteContinueWatchingItem = null
                },
                onConfirm = {
                    Log.d(TAG, "HOME: Delete confirmed for ${item.title}")
                    scope.launch {
                        val contentId = item.providerId.orEmpty().ifBlank { item.stableId.orEmpty().substringAfterLast(":") }
                        if (isSeries) {
                            val seriesName = item.seriesName ?: item.title
                            Log.d(TAG, "Deleting all episodes for series: $seriesName")
                            deleteAllSeriesProgress(seriesName)
                        } else {
                            watchProgressRepo.deleteProgress(contentId)
                            Log.d(TAG, "Deleted continue watching: $contentId")
                        }
                        deleteContinueWatchingItem = null
                        loadContinueWatching()
                    }
                },
            )
        }
    }

    @Composable
    private fun ContentSection(
        section: BrowseSection,
        onFocused: (CatalogItem) -> Unit,
        onLoadMore: ((BrowseSection, () -> Unit) -> Unit)? = null,
    ) {
        val lazyListState = rememberLazyListState()
        var isLoadingMore by remember { mutableStateOf(false) }

        LaunchedEffect(section.items) {
            if (section.items.firstOrNull()?.kind == ContentKind.EVENT) {
                val index = findNextEventIndex(section.items)
                if (index > 0) lazyListState.scrollToItem(index)
            }
        }

        LaunchedEffect(lazyListState, section.hasNextPage, onLoadMore, section.currentPage) {
            if (onLoadMore == null || !section.hasNextPage || isLoadingMore) return@LaunchedEffect
            snapshotFlow { lazyListState.layoutInfo }
                .map { info ->
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    val totalItems = info.totalItemsCount
                    lastVisible to totalItems
                }
                .distinctUntilChanged()
                .collect { (lastVisible, totalItems) ->
                    if (totalItems > 0 && lastVisible >= totalItems - 5) {
                        isLoadingMore = true
                        onLoadMore(section) { isLoadingMore = false }
                    }
                }
        }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(section.title, color = IptvTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                section.contentType?.let { type ->
                    if (type == "movies" || type == "series") {
                        Text(
                            text = if (type == "movies") "PELICULAS" else "SERIES",
                            color = if (type == "movies") Color(0xFFE91E63) else Color(0xFF2196F3),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            LazyRow(state = lazyListState, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(section.items) { item ->
                    if (section.title == "Continuar viendo") {
                        ContinueWatchingCard(
                            item = item,
                            progressPercent = continueWatchingEntries[item.stableId]?.progressPercent ?: 0,
                            isWatched = continueWatchingEntries[item.stableId]?.isWatched == true,
                            onFocused = { onFocused(item) },
                            onDeleteRequest = {
                                Log.d(TAG, "CW_DELETE: onDeleteRequest called for ${it.title}")
                                deleteContinueWatchingItem = it
                            }
                        )
                    } else {
                        val itemWithWatched = if (item.kind == ContentKind.MOVIE || item.kind == ContentKind.SERIES) {
                            val wp = continueWatchingEntries[item.stableId]
                            item.copy(isWatched = wp?.isWatched == true)
                        } else item
                        MediaCard(item = itemWithWatched, onFocused = { onFocused(item) }) { handleCardClick(item, section.items) }
                    }
                }
            }
        }
    }

    @Composable
    private fun ContinueWatchingCard(
        item: CatalogItem,
        progressPercent: Int = 0,
        isWatched: Boolean = false,
        onFocused: (CatalogItem) -> Unit,
        onDeleteRequest: (CatalogItem) -> Unit,
    ) {
        var isFocused by remember { mutableStateOf(false) }
        val isChannelOrEvent = item.kind == ContentKind.CHANNEL || item.kind == ContentKind.EVENT
        val cardWidth = if (isChannelOrEvent) 190.dp else 140.dp
        val imageHeight = if (isChannelOrEvent) 107.dp else 200.dp
        val scope = rememberCoroutineScope()

        var longPressTriggered by remember { mutableStateOf(false) }
        var longPressJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        var cardLongPressDialogShown by remember { mutableStateOf(false) }

        val interactionSource = remember { MutableInteractionSource() }

        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction: Interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        Log.d(TAG, "CW_PRESS: Press detected, dialogVisible=${deleteContinueWatchingItem != null}")
                        if (deleteContinueWatchingItem != null) {
                            Log.d(TAG, "CW_PRESS: Dialog already visible, ignoring press")
                            return@collect
                        }
                        longPressTriggered = false
                        longPressJob = scope.launch {
                            delay(800L)
                            Log.d(TAG, "CW_PRESS: Timer expired, calling onDeleteRequest for ${item.title}")
                            longPressTriggered = true
                            cardLongPressDialogShown = true
                            onDeleteRequest(item)
                        }
                    }
                    is PressInteraction.Release -> {
                        Log.d(TAG, "CW_RELEASE: Release detected, dialogVisible=${deleteContinueWatchingItem != null}, longPressTriggered=$longPressTriggered, dialogShown=$cardLongPressDialogShown")
                        longPressJob?.cancel()
                        longPressJob = null
                        if (deleteContinueWatchingItem == null && !longPressTriggered) {
                            Log.d(TAG, "CW_RELEASE: Executing click for ${item.title}")
                            handleCardClick(item, listOf(item))
                        } else {
                            Log.d(TAG, "CW_RELEASE: Skipping click (dialog visible or longPress was triggered)")
                        }
                        longPressTriggered = false
                    }
                    is PressInteraction.Cancel -> {
                        Log.d(TAG, "CW_CANCEL: Cancel detected")
                        longPressJob?.cancel()
                        longPressJob = null
                        longPressTriggered = false
                    }
                    else -> {}
                }
            }
        }

        Column(
            modifier = Modifier
                .width(cardWidth)
                .background(if (isFocused) IptvFocusBg else IptvCard, RoundedCornerShape(10.dp))
                .border(
                    if (isFocused) 2.dp else 1.dp,
                    if (isFocused) IptvFocusBorder else IptvSurfaceVariant,
                    RoundedCornerShape(10.dp)
                )
                .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocused(item) }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    // Click handled in LaunchedEffect above
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .background(IptvSurfaceVariant, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.imageUrl.isNotBlank()) {
                    RemoteImage(
                        url = item.imageUrl,
                        width = 300,
                        height = 400,
                        scaleType = if (item.kind == ContentKind.CHANNEL) ScaleType.FIT_CENTER else ScaleType.CENTER_CROP
                    )
                } else {
                    PlaceholderIcon(kind = item.kind)
                }
                if (isWatched) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = "Visto",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "VISTO",
                            color = Color(0xFF4CAF50),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (progressPercent in 1..99) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(IptvSurfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressPercent / 100f)
                            .background(IptvAccent),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (item.subtitle.isNotBlank()) 78.dp else 56.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                val displayTitle = when {
                    item.kind == ContentKind.SERIES && !item.seriesName.isNullOrBlank() -> item.seriesName
                    else -> item.normalizedTitle?.takeUnless { it.equals("null", ignoreCase = true) }?.takeIf { it.isNotBlank() } ?: item.title.takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
                }
                Text(displayTitle, color = IptvTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, color = IptvTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    // ── Delete Confirmation Overlay ─────────────────────────────────────────────

    @Composable
    private fun DeleteConfirmationOverlay(
        item: CatalogItem,
        isSeries: Boolean,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit,
    ) {
        val dialogMessage = if (isSeries) {
            "¿Quieres eliminar toda la serie \"${item.seriesName ?: item.title}\" de tu historial de reproducción?"
        } else {
            "¿Quieres eliminar \"${item.title}\" de tu historial de reproducción?"
        }

        val focusRequester = remember { FocusRequester() }
        var selectedButton by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> {
                                Log.d(TAG, "DIALOG_KEY: LEFT pressed, selectedButton=$selectedButton")
                                selectedButton = 1
                                true
                            }
                            Key.DirectionRight -> {
                                Log.d(TAG, "DIALOG_KEY: RIGHT pressed, selectedButton=$selectedButton")
                                selectedButton = 2
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                Log.d(TAG, "DIALOG_KEY: CENTER/ENTER pressed, selectedButton=$selectedButton")
                                when (selectedButton) {
                                    1 -> {
                                        Log.d(TAG, "DIALOG_KEY: Executing cancel")
                                        onDismiss()
                                    }
                                    2 -> {
                                        Log.d(TAG, "DIALOG_KEY: Executing delete")
                                        onConfirm()
                                    }
                                    else -> {
                                        Log.d(TAG, "DIALOG_KEY: No button selected, doing nothing")
                                    }
                                }
                                true
                            }
                            Key.Back, Key.Escape -> {
                                Log.d(TAG, "DIALOG_KEY: BACK/ESC pressed, closing")
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(400.dp)
                        .background(Color(0xFF1A1A2E), RoundedCornerShape(16.dp))
                        .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            "Eliminar de Continuar viendo",
                            color = IptvTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            dialogMessage,
                            color = IptvTextMuted,
                            fontSize = 14.sp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedButton == 1) IptvFocusBg else Color.Transparent, RoundedCornerShape(8.dp))
                                    .border(if (selectedButton == 1) 2.dp else 0.dp, if (selectedButton == 1) IptvFocusBorder else Color.Transparent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clickable { onDismiss() },
                            ) {
                                Text("Cancelar", color = if (selectedButton == 1) IptvTextPrimary else IptvTextMuted, fontSize = 14.sp)
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedButton == 2) IptvLive.copy(alpha = 0.8f) else IptvLive, RoundedCornerShape(8.dp))
                                    .border(if (selectedButton == 2) 2.dp else 0.dp, if (selectedButton == 2) IptvTextPrimary else Color.Transparent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clickable { onConfirm() },
                            ) {
                                Text("Eliminar", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Guide (TV / Events) ───────────────────────────────────────────────────

    @Composable
    private fun GuideContent(kind: ContentKind) {
        Log.d(TAG, "GuideContent ENTERED kind=$kind")
        val isEventGuide = kind == ContentKind.EVENT
        var selectedCountry by remember { mutableStateOf(ALL_OPTION) }
        var selectedGroup by remember { mutableStateOf(ALL_OPTION) }
        var searchQuery by remember { mutableStateOf("") }
        var showCountryDialog by remember { mutableStateOf(false) }
        var showGroupDialog by remember { mutableStateOf(false) }
        val countryFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        val groupFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        val lazyGridState = rememberLazyGridState()

        var eventItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
        val loader = remember(kind) { PagedContentLoader(contentCacheManager, repository, kind) }
        var displayItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
        var totalCount by remember { mutableStateOf(0) }
        var currentPage by remember { mutableStateOf(0) }
        var isLoadingPage by remember { mutableStateOf(false) }
        val pageSize = 50

        var filteredGroupOptions by remember { mutableStateOf<List<CatalogFilterOption>>(emptyList()) }

        val countryOptions = remember(kind, channelFilters) {
            if (isEventGuide) {
                listOf(CatalogFilterOption(ALL_OPTION, "Todos"))
            } else {
                buildList {
                    add(CatalogFilterOption(ALL_OPTION, "Todos"))
                    channelFilters.countries.forEach(::add)
                }
            }
        }

        LaunchedEffect(selectedCountry, channelFilters) {
            if (isEventGuide) return@LaunchedEffect
            val country = selectedCountry.takeUnless { it == ALL_OPTION }
            val groups = if (country != null) {
                contentCacheManager.getChannelsByCountry(country)
                    .distinctBy { it.grupoNormalizado }
                    .filter { it.grupoNormalizado.isNotBlank() }
                    .map { CatalogFilterOption(it.grupoNormalizado, it.grupoNormalizado) }
            } else {
                channelFilters.groups
                    .distinctBy { it.value }
                    .filter { it.value != "Favorites" && it.value != "Favoritos" }
            }
            filteredGroupOptions = buildList {
                add(CatalogFilterOption(ALL_OPTION, "Todos"))
                addAll(groups)
            }
        }

        val groupOptions = if (isEventGuide) {
            remember(eventItems) {
                buildList {
                    add(CatalogFilterOption(ALL_OPTION, "Todos"))
                    eventItems
                        .map { it.group.trim() }
                        .filter(String::isNotBlank)
                        .distinct()
                        .sorted()
                        .forEach { add(CatalogFilterOption(it, it)) }
                }
            }
        } else {
            filteredGroupOptions.ifEmpty {
                remember(channelFilters) {
                    buildList {
                        add(CatalogFilterOption(ALL_OPTION, "Todos"))
                        channelFilters.groups
                            .distinctBy { it.value }
                            .filter { it.value != "Favorites" && it.value != "Favoritos" }
                            .forEach(::add)
                    }
                }
            }
        }

        val displayItemsForGrid = remember(displayItems) {
            if (isEventGuide) {
                displayItems.sortedWith(
                    compareBy<CatalogItem> { it.badgeText }
                        .thenBy { it.title },
                )
            } else {
                displayItems.sortedBy { it.channelNumber ?: Int.MAX_VALUE }
            }
        }

        LaunchedEffect(displayItemsForGrid, currentItem) {
            if (!isEventGuide && displayItemsForGrid.isNotEmpty()) {
                val current = currentItem
                if (current != null) {
                    val idx = displayItemsForGrid.indexOfFirst { it.stableId == current.stableId }
                    if (idx > 0) {
                        lazyGridState.scrollToItem(maxOf(0, idx - 1))
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            val initial = guideInitialGroup
            if (initial != null) {
                guideInitialGroup = null
                val match = groupOptions.firstOrNull { it.value == initial || it.label == initial }
                if (match != null) {
                    selectedGroup = match.value
                }
            }
        }

        LaunchedEffect(kind) {
            if (isEventGuide) {
                Log.d(TAG, "GuideContent: loading events")
                runCatching { repository.loadEventsOnly() }
                    .onSuccess { catalog ->
                        eventItems = catalog.sections
                            .flatMap(BrowseSection::items)
                            .distinctBy(CatalogItem::stableId)
                        displayItems = eventItems
                        totalCount = displayItems.size
                        Log.d(TAG, "GuideContent: events loaded count=${eventItems.size}")
                    }
                    .onFailure { Log.e(TAG, "No se pudieron cargar los eventos", it) }
            }
        }

        LaunchedEffect(Unit) {
            if (!isEventGuide) {
                kotlinx.coroutines.delay(100)
            }
        }

        var lastLoadKey by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }

        LaunchedEffect(selectedCountry, selectedGroup, searchQuery) {
            if (isEventGuide) return@LaunchedEffect

            val key = "$selectedCountry|$selectedGroup|$searchQuery"
            if (key == lastLoadKey || isLoading) return@LaunchedEffect
            lastLoadKey = key
            isLoading = true

            loader.clear()
            currentPage = 0
            isLoadingPage = false

            if (searchQuery.isNotBlank()) {
                loader.loadSearch(searchQuery)
                displayItems = loader.getDisplayItems()
                totalCount = loader.getTotalCount()
            } else {
                val country = selectedCountry.takeUnless { it == ALL_OPTION }
                val group = selectedGroup.takeUnless { it == ALL_OPTION }
                loader.refreshTotalCount(country, group)
                totalCount = loader.getTotalCount()
                loader.loadPage(0, country, group)
                displayItems = loader.getDisplayItems()
            }
            isLoading = false
        }

        LaunchedEffect(displayItemsForGrid) {
            if (isEventGuide && displayItemsForGrid.isNotEmpty()) {
                val index = findNextEventIndex(displayItemsForGrid)
                if (index > 0) lazyGridState.scrollToItem(index)
            }
        }

        LaunchedEffect(selectedCountry) {
            if (isEventGuide) return@LaunchedEffect
            searchQuery = ""
            selectedGroup = ALL_OPTION
        }

        LaunchedEffect(lazyGridState, searchQuery) {
            if (isEventGuide) return@LaunchedEffect
            if (searchQuery.isNotBlank()) return@LaunchedEffect

            snapshotFlow { lazyGridState.layoutInfo }
                .map { info -> (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount }
                .distinctUntilChanged()
                .filter { (lastVisibleIndex, totalItemsCount) ->
                    lastVisibleIndex >= 0 && totalItemsCount > 0 && lastVisibleIndex >= totalItemsCount - 10
                }
                .collect {
                    if (isLoadingPage || loader.isCurrentlyLoading()) return@collect
                    val nextPage = currentPage + 1
                    val maxPages = (totalCount + pageSize - 1) / pageSize
                    if (nextPage >= maxPages) return@collect
                    if (!loader.isPageLoaded(nextPage)) {
                        isLoadingPage = true
                        val country = selectedCountry.takeUnless { it == ALL_OPTION }
                        val group = selectedGroup.takeUnless { it == ALL_OPTION }
                        loader.loadPage(nextPage, country, group)
                        displayItems = loader.getDisplayItems()
                        currentPage = nextPage
                        isLoadingPage = false
                    }
                }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ScreenHeader(title = screenTitle(kind), subtitle = if (!isEventGuide) "$totalCount canales disponibles" else "")
            FilterTopBar(
                showIdioma = kind == ContentKind.CHANNEL,
                selectedIdioma = countryOptions.firstOrNull { it.value == selectedCountry }?.label ?: selectedCountry,
                selectedGrupo = groupOptions.firstOrNull { it.value == selectedGroup }?.label ?: selectedGroup,
                onIdiomaClicked = { showCountryDialog = true },
                onGrupoClicked = { showGroupDialog = true },
                idiomaFocusRequester = countryFocusRequester,
                grupoFocusRequester = groupFocusRequester,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                searchFocusRequester = searchFocusRequester,
                idiomaLabel = COUNTRY_FILTER_LABEL,
            )
            if (displayItemsForGrid.isEmpty() && !isLoadingPage) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (!isEventGuide && channelFilters.countries.isEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Cargando categorias...", color = IptvTextMuted, fontSize = 18.sp)
                        }
                    } else {
                        Text(
                            if (searchQuery.isNotBlank()) "No hay resultados para \"$searchQuery\"" else "No hay contenido disponible",
                            color = IptvTextMuted, fontSize = 18.sp,
                        )
                    }
                }
            } else {
                val gridColumns = if (isEventGuide) 5 else 3
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    state = lazyGridState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(displayItemsForGrid.size) { index ->
                        val item = displayItemsForGrid[index]
                        val isCurrentChannel = currentItem?.stableId == item.stableId
                        if (isEventGuide) {
                            MediaCard(item = item, onFocused = { selectedHero = item }) { handleCardClick(item, displayItemsForGrid) }
                        } else {
                            EpgChannelCard(
                                item = item,
                                isCurrentChannel = isCurrentChannel,
                                onFocused = { selectedHero = item }
                            ) { handleCardClick(item, displayItemsForGrid) }
                        }
                    }
                    if (isLoadingPage) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("Cargando...", color = IptvTextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (showCountryDialog) {
            FilterDialog(
                title = "Selecciona pais",
                options = countryOptions,
                selectedOption = selectedCountry,
                onOptionSelected = { selectedCountry = it.value; showCountryDialog = false },
                onDismiss = { showCountryDialog = false },
            )
        }
        if (showGroupDialog) {
            FilterDialog(
                title = "Selecciona grupo",
                options = groupOptions,
                selectedOption = selectedGroup,
                onOptionSelected = { selectedGroup = it.value; showGroupDialog = false },
                onDismiss = { showGroupDialog = false },
            )
        }
    }

    // ── Channel Picker Dialog ─────────────────────────────────────────────────

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    private fun ChannelPickerDialog(
        currentCountry: String,
        currentGroup: String,
        searchQuery: String,
        showFavorites: Boolean,
        onCountryChange: (String) -> Unit,
        onGroupChange: (String) -> Unit,
        onFavoritesChange: (Boolean) -> Unit,
        onSearchChange: (String) -> Unit,
        onChannelSelected: (CatalogItem) -> Unit,
        onDismiss: () -> Unit
    ) {
        var selectedIndex by remember { mutableStateOf(0) }
        var activePanel by remember { mutableStateOf(2) }

        val channelListState = rememberLazyListState()
        val countryListState = rememberLazyListState()
        val groupListState = rememberLazyListState()
        val listFocusRequester = remember { FocusRequester() }
        val countryFocusRequester = remember { FocusRequester() }
        val groupFocusRequester = remember { FocusRequester() }

        val loader = remember { PagedContentLoader(contentCacheManager, repository, ContentKind.CHANNEL) }
        var displayChannels by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
        var currentPage by remember { mutableStateOf(0) }
        var isLoadingPage by remember { mutableStateOf(false) }
        var totalCount by remember { mutableStateOf(0) }
        val pageSize = 50

        val countryOptions = remember(channelFilters) {
            buildList {
                add(CatalogFilterOption(ALL_OPTION, "Todos los países"))
                channelFilters.countries.forEach(::add)
            }
        }

        var groupOptions by remember { mutableStateOf<List<CatalogFilterOption>>(emptyList()) }

        LaunchedEffect(currentCountry) {
            val country = currentCountry.takeUnless { it == ALL_OPTION }
            val groups = if (country != null) {
                contentCacheManager.getChannelsByCountry(country)
                    .distinctBy { it.grupoNormalizado }
                    .filter { it.grupoNormalizado.isNotBlank() }
                    .map { CatalogFilterOption(it.grupoNormalizado, it.grupoNormalizado) }
            } else {
                channelFilters.groups
                    .distinctBy { it.value }
                    .filter { it.value != "Favorites" && it.value != "Favoritos" }
            }
            groupOptions = buildList {
                add(CatalogFilterOption(ALL_OPTION, "Todas las categorías"))
                add(CatalogFilterOption("__favs__", "⭐ Favoritos"))
                addAll(groups)
            }
        }

        val panelBg = Color(0xFF0D0D1E).copy(alpha = 0.92f)
        val activePanelBg = Color(0xFF13132B).copy(alpha = 0.96f)
        val selectedItemBg = Color(0xFF2A2A6E).copy(alpha = 0.85f)
        val dividerColor = Color(0xFF2D2D60).copy(alpha = 0.6f)
        val accentColor = IptvAccent
        val borderGlow = Color(0xFF4444AA).copy(alpha = 0.5f)

        LaunchedEffect(currentCountry, currentGroup, searchQuery, showFavorites) {
            loader.clear()
            currentPage = 0
            isLoadingPage = false

            when {
                searchQuery.isNotBlank() -> {
                    loader.loadSearch(searchQuery)
                    displayChannels = loader.getDisplayItems()
                    totalCount = displayChannels.size
                }
                showFavorites -> {
                    val favs = runCatching { repository.loadFavoriteChannels() }.getOrDefault(emptyList())
                    displayChannels = favs.sortedBy { it.channelNumber ?: Int.MAX_VALUE }
                    totalCount = displayChannels.size
                }
                else -> {
                    val country = currentCountry.takeUnless { it == ALL_OPTION }
                    val group = currentGroup.takeUnless { it == ALL_OPTION }
                    loader.refreshTotalCount(country, group)
                    totalCount = loader.getTotalCount()
                    loader.loadPage(0, country, group)
                    displayChannels = loader.getDisplayItems()
                }
            }

            val current = currentItem
            selectedIndex = if (current != null) {
                displayChannels.indexOfFirst { it.stableId == current.stableId }.coerceAtLeast(0)
            } else 0
        }

        LaunchedEffect(selectedIndex, displayChannels.size) {
            if (selectedIndex in displayChannels.indices) {
                kotlinx.coroutines.delay(10)
                channelListState.scrollToItem(selectedIndex)
            }
        }

        LaunchedEffect(channelListState, searchQuery, showFavorites) {
            if (searchQuery.isNotBlank() || showFavorites) return@LaunchedEffect
            snapshotFlow { channelListState.layoutInfo }
                .map { info -> (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount }
                .distinctUntilChanged()
                .filter { (last, total) -> last >= 0 && total > 0 && last >= total - 10 }
                .collect {
                    if (isLoadingPage || loader.isCurrentlyLoading()) return@collect
                    val nextPage = currentPage + 1
                    val maxPages = (totalCount + pageSize - 1) / pageSize
                    if (nextPage >= maxPages || loader.isPageLoaded(nextPage)) return@collect
                    isLoadingPage = true
                    val country = currentCountry.takeUnless { it == ALL_OPTION }
                    val group = currentGroup.takeUnless { it == ALL_OPTION }
                    loader.loadPage(nextPage, country, group)
                    displayChannels = loader.getDisplayItems()
                    currentPage = nextPage
                    isLoadingPage = false
                }
        }

        Box(
            modifier = Modifier
                .fillMaxSize(0.90f)
                .background(Color(0xFF080818).copy(alpha = 0.88f), RoundedCornerShape(16.dp))
                .border(1.dp, borderGlow, RoundedCornerShape(16.dp)),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {

                // Panel 1: País
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight()
                        .background(if (activePanel == 0) activePanelBg else panelBg, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A1A40).copy(alpha = 0.7f), RoundedCornerShape(topStart = 16.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🌍", fontSize = 16.sp)
                            Text("País", color = if (activePanel == 0) accentColor else IptvTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                    LazyColumn(
                        state = countryListState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(countryOptions, key = { "c_${it.value}" }) { option ->
                            val isSelected = currentCountry == option.value
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) selectedItemBg else Color.Transparent)
                                    .border(if (isSelected) 1.dp else 0.dp, if (isSelected) accentColor.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable {
                                        onCountryChange(option.value)
                                        onGroupChange(ALL_OPTION)
                                        onFavoritesChange(false)
                                        activePanel = 1
                                        kotlinx.coroutines.GlobalScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            runCatching { groupFocusRequester.requestFocus() }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) Box(modifier = Modifier.size(6.dp).background(accentColor, RoundedCornerShape(50)))
                                    else Spacer(modifier = Modifier.size(6.dp))
                                    Text(option.label, color = if (isSelected) IptvTextPrimary else IptvTextMuted, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(dividerColor))

                // Panel 2: Categoría
                Column(
                    modifier = Modifier
                        .width(210.dp)
                        .fillMaxHeight()
                        .background(if (activePanel == 1) activePanelBg else panelBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A1A40).copy(alpha = 0.7f))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📂", fontSize = 16.sp)
                            Text("Categoría", color = if (activePanel == 1) accentColor else IptvTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                    LazyColumn(
                        state = groupListState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(groupOptions, key = { "g_${it.value}" }) { option ->
                            val isSelected = if (option.value == "__favs__") showFavorites else (!showFavorites && currentGroup == option.value)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) selectedItemBg else Color.Transparent)
                                    .border(if (isSelected) 1.dp else 0.dp, if (isSelected) accentColor.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (option.value == "__favs__") onFavoritesChange(true)
                                        else { onFavoritesChange(false); onGroupChange(option.value) }
                                        selectedIndex = 0
                                        activePanel = 2
                                        kotlinx.coroutines.GlobalScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            runCatching { listFocusRequester.requestFocus() }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) Box(modifier = Modifier.size(6.dp).background(accentColor, RoundedCornerShape(50)))
                                    else Spacer(modifier = Modifier.size(6.dp))
                                    Text(option.label, color = if (isSelected) IptvTextPrimary else IptvTextMuted, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(dividerColor))

                // Panel 3: Canales
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (activePanel == 2) activePanelBg else panelBg, RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A1A40).copy(alpha = 0.7f), RoundedCornerShape(topEnd = 16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.LiveTv, contentDescription = null, tint = if (activePanel == 2) accentColor else IptvTextSecondary, modifier = Modifier.size(16.dp))
                            Text("Canales", color = if (activePanel == 2) accentColor else IptvTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            val countLabel = when {
                                searchQuery.isNotBlank() -> "${displayChannels.size}"
                                showFavorites -> "${displayChannels.size} ⭐"
                                else -> "$totalCount"
                            }
                            Text(countLabel, color = IptvTextMuted, fontSize = 11.sp)
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .focusable()
                            .onFocusChanged { if (it.hasFocus) activePanel = 2 }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionUp -> { if (selectedIndex > 0) selectedIndex--; true }
                                        Key.DirectionDown -> { if (selectedIndex < displayChannels.size - 1) selectedIndex++; true }
                                        Key.DirectionLeft -> { activePanel = 1; true }
                                        Key.Enter, Key.DirectionCenter -> { displayChannels.getOrNull(selectedIndex)?.let { onChannelSelected(it) }; true }
                                        Key.Back, Key.Escape -> { onDismiss(); true }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        if (displayChannels.isEmpty() && !isLoadingPage) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if (searchQuery.isNotBlank()) "Sin resultados" else "No hay canales disponibles", color = IptvTextMuted, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(
                                state = channelListState,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                items(displayChannels.size) { index ->
                                    val item = displayChannels[index]
                                    val isHighlighted = index == selectedIndex
                                    val isPlaying = currentItem?.stableId == item.stableId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(when { isPlaying && isHighlighted -> accentColor.copy(alpha = 0.28f); isPlaying -> accentColor.copy(alpha = 0.14f); isHighlighted -> selectedItemBg; else -> Color.Transparent })
                                            .border(if (isPlaying || isHighlighted) 1.dp else 0.dp, when { isPlaying -> accentColor.copy(alpha = 0.7f); isHighlighted -> IptvFocusBorder.copy(alpha = 0.6f); else -> Color.Transparent }, RoundedCornerShape(8.dp))
                                            .clickable { onChannelSelected(item) }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        item.channelNumber?.let { num ->
                                            Text(num.toString().padStart(3, ' '), color = if (isPlaying) accentColor else IptvTextMuted.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp), textAlign = TextAlign.End)
                                        } ?: Box(modifier = Modifier.width(26.dp))
                                        Box(modifier = Modifier.size(34.dp).background(Color(0xFF1A1A40).copy(alpha = 0.8f), RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                            if (item.imageUrl.isNotBlank()) RemoteImage(url = item.imageUrl, width = 64, height = 64, scaleType = ScaleType.FIT_CENTER)
                                            else Icon(Icons.Outlined.LiveTv, contentDescription = null, tint = IptvTextMuted.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                            Text(item.title, color = when { isPlaying -> accentColor; isHighlighted -> IptvTextPrimary; else -> IptvTextSecondary }, fontSize = 13.sp, fontWeight = if (isHighlighted || isPlaying) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (item.group.isNotBlank()) Text(item.group, color = IptvTextMuted.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (isPlaying) Box(modifier = Modifier.background(IptvLive.copy(alpha = 0.85f), RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) { Text("▶", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                                    }
                                }
                                if (isLoadingPage) {
                                    item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Text("Cargando...", color = IptvTextMuted, fontSize = 12.sp) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── VOD grid (Movies / Series) ────────────────────────────────────────────

    @Composable
    private fun VodGridContent(kind: ContentKind) {
        var selectedCountry by remember { mutableStateOf(ALL_OPTION) }
        var selectedGroup   by remember { mutableStateOf(ALL_OPTION) }
        var searchQuery     by remember { mutableStateOf("") }
        var showCountryDialog by remember { mutableStateOf(false) }
        var showGroupDialog   by remember { mutableStateOf(false) }
        val countryFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        val groupFocusRequester   = remember { androidx.compose.ui.focus.FocusRequester() }
        val searchFocusRequester  = remember { androidx.compose.ui.focus.FocusRequester() }
        val lazyGridState = rememberLazyGridState()

        val loader = remember(kind) { PagedContentLoader(contentCacheManager, repository, kind) }
        var displayItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
        var totalCount by remember { mutableStateOf(0) }
        var currentPage by remember { mutableStateOf(0) }
        var isLoadingPage by remember { mutableStateOf(false) }
        val pageSize = 50

        val currentFilters = if (kind == ContentKind.MOVIE) movieFilters else seriesFilters

        val countryOptions = remember(currentFilters) {
            buildList {
                add(CatalogFilterOption(ALL_OPTION, "Todos"))
                currentFilters.countries.forEach(::add)
            }
        }
        var groupOptions by remember { mutableStateOf<List<CatalogFilterOption>>(emptyList()) }

        LaunchedEffect(selectedCountry, currentFilters) {
            val country = selectedCountry.takeUnless { it == ALL_OPTION }
            val groups = if (country != null) {
                if (kind == ContentKind.MOVIE) {
                    contentCacheManager.getMoviesByCountry(country)
                        .distinctBy { it.grupoNormalizado }
                        .filter { it.grupoNormalizado.isNotBlank() }
                        .map { CatalogFilterOption(it.grupoNormalizado, it.grupoNormalizado) }
                } else {
                    contentCacheManager.getSeriesByCountry(country)
                        .distinctBy { it.grupoNormalizado }
                        .filter { it.grupoNormalizado.isNotBlank() }
                        .map { CatalogFilterOption(it.grupoNormalizado, it.grupoNormalizado) }
                }
            } else {
                currentFilters.groups
                    .distinctBy { it.value }
                    .filter { it.value != "Favorites" && it.value != "Favoritos" }
            }
            groupOptions = buildList {
                add(CatalogFilterOption(ALL_OPTION, "Todos"))
                addAll(groups)
            }
        }

        val displayItemsForGrid = remember(displayItems) { displayItems.sortedBy { it.title } }

        var lastLoadKey by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }

        var searchDebounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        // Reset group when country changes
        LaunchedEffect(selectedCountry) {
            selectedGroup = ALL_OPTION
        }

        // Debounced search: wait 500ms after last keystroke before executing
        LaunchedEffect(searchQuery) {
            searchDebounceJob?.cancel()
            searchDebounceJob = launch {
                kotlinx.coroutines.delay(500)
                val key = "$selectedCountry|$selectedGroup|$searchQuery"
                if (key == lastLoadKey) return@launch
                lastLoadKey = key
                isLoading = true
                Log.d(TAG, "VodGridContent: search debounce fired for '$searchQuery', kind=$kind")
                loader.clear()
                currentPage = 0
                isLoadingPage = false
                if (searchQuery.isNotBlank()) {
                    Log.d(TAG, "VodGridContent: executing search for '$searchQuery'")
                    loader.loadSearch(searchQuery)
                    displayItems = loader.getDisplayItems()
                    totalCount = loader.getTotalCount()
                    Log.d(TAG, "VodGridContent: search returned ${displayItems.size} items")
                } else {
                    val country = selectedCountry.takeUnless { it == ALL_OPTION }
                    val group = selectedGroup.takeUnless { it == ALL_OPTION }
                    loader.refreshTotalCount(country, group)
                    totalCount = loader.getTotalCount()
                    loader.loadPage(0, country, group)
                    displayItems = loader.getDisplayItems()
                }
                isLoading = false
            }
        }

        // Load initial data when country/group changes (not search)
        LaunchedEffect(selectedCountry, selectedGroup) {
            if (searchQuery.isNotBlank()) return@LaunchedEffect
            val key = "$selectedCountry|$selectedGroup|"
            if (key == lastLoadKey || isLoading) return@LaunchedEffect
            lastLoadKey = key
            isLoading = true
            Log.d(TAG, "VodGridContent: loading with key='$key'")
            loader.clear()
            currentPage = 0
            isLoadingPage = false
            val country = selectedCountry.takeUnless { it == ALL_OPTION }
            val group = selectedGroup.takeUnless { it == ALL_OPTION }
            loader.refreshTotalCount(country, group)
            totalCount = loader.getTotalCount()
            loader.loadPage(0, country, group)
            displayItems = loader.getDisplayItems()
            isLoading = false
        }

        LaunchedEffect(lazyGridState, searchQuery) {
            if (searchQuery.isNotBlank()) return@LaunchedEffect
            snapshotFlow { lazyGridState.layoutInfo }
                .map { info -> (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount }
                .distinctUntilChanged()
                .filter { (lastVisibleIndex, totalItemsCount) -> lastVisibleIndex >= 0 && totalItemsCount > 0 && lastVisibleIndex >= totalItemsCount - 10 }
                .collect {
                    if (isLoadingPage || loader.isCurrentlyLoading()) return@collect
                    val nextPage = currentPage + 1
                    val maxPages = (totalCount + pageSize - 1) / pageSize
                    if (nextPage >= maxPages) return@collect
                    if (!loader.isPageLoaded(nextPage)) {
                        isLoadingPage = true
                        val country = selectedCountry.takeUnless { it == ALL_OPTION }
                        val group = selectedGroup.takeUnless { it == ALL_OPTION }
                        loader.loadPage(nextPage, country, group)
                        displayItems = loader.getDisplayItems()
                        currentPage = nextPage
                        isLoadingPage = false
                    }
                }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader(title = screenTitle(kind), subtitle = "")
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FilterTopBar(
                    showIdioma           = true,
                    selectedIdioma       = countryOptions.firstOrNull { it.value == selectedCountry }?.label ?: selectedCountry,
                    selectedGrupo        = groupOptions.firstOrNull { it.value == selectedGroup }?.label ?: selectedGroup,
                    onIdiomaClicked      = { showCountryDialog = true },
                    onGrupoClicked       = { showGroupDialog = true },
                    idiomaFocusRequester = countryFocusRequester,
                    grupoFocusRequester  = groupFocusRequester,
                    searchQuery          = searchQuery,
                    onSearchQueryChange  = { searchQuery = it },
                    searchFocusRequester = searchFocusRequester,
                    idiomaLabel          = "Idioma",
                )
                if (displayItemsForGrid.isEmpty() && !isLoadingPage) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (searchQuery.isNotBlank()) "No hay resultados para \"$searchQuery\"" else "No hay contenido disponible", color = IptvTextMuted, fontSize = 18.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns               = GridCells.Fixed(5),
                        state                 = lazyGridState,
                        modifier              = Modifier.weight(1f),
                        contentPadding        = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement   = Arrangement.spacedBy(14.dp),
                    ) {
                        gridItems(displayItemsForGrid) { item ->
                            MediaCard(item = item, onFocused = { selectedHero = item }) { handleCardClick(item, displayItemsForGrid) }
                        }
                        if (isLoadingPage) {
                            item { Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("Cargando...", color = IptvTextMuted, fontSize = 14.sp) } }
                        }
                    }
                }
            }
        }

        if (showCountryDialog) {
            FilterDialog(title = COUNTRY_FILTER_DIALOG_TITLE, options = countryOptions, selectedOption = selectedCountry, onOptionSelected = { selectedCountry = it.value; showCountryDialog = false }, onDismiss = { showCountryDialog = false })
        }
        if (showGroupDialog) {
            FilterDialog(title = "Selecciona grupo", options = groupOptions, selectedOption = selectedGroup, onOptionSelected = { selectedGroup = it.value; showGroupDialog = false }, onDismiss = { showGroupDialog = false })
        }
    }

    // ── Channel card (EPG compact) ────────────────────────────────────────────

    @Composable
    private fun EpgChannelCard(item: CatalogItem, isCurrentChannel: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
        var isFocused by remember { mutableStateOf(false) }
        val bgColor = when { isCurrentChannel && isFocused -> IptvAccent.copy(alpha = 0.35f); isCurrentChannel -> IptvAccent.copy(alpha = 0.18f); isFocused -> IptvFocusBg; else -> IptvCard.copy(alpha = 0.7f) }
        val borderColor = when { isCurrentChannel -> IptvAccent; isFocused -> IptvFocusBorder; else -> IptvSurfaceVariant.copy(alpha = 0.5f) }
        val borderWidth = if (isFocused || isCurrentChannel) 2.dp else 1.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(bgColor, RoundedCornerShape(10.dp))
                .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
                .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocused() }
                .clickable { onClick() }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item.channelNumber?.let { num ->
                Text(text = num.toString().padStart(3, ' '), color = if (isCurrentChannel) IptvAccent else IptvTextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
            } ?: Box(modifier = Modifier.width(36.dp))
            Box(modifier = Modifier.size(48.dp).background(IptvSurfaceVariant, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                if (item.imageUrl.isNotBlank()) RemoteImage(url = item.imageUrl, width = 80, height = 80, scaleType = ScaleType.FIT_CENTER)
                else Icon(Icons.Outlined.LiveTv, contentDescription = null, tint = IptvTextMuted, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(text = item.title, color = if (isCurrentChannel) IptvAccent else IptvTextPrimary, fontSize = 15.sp, fontWeight = if (isCurrentChannel) FontWeight.Bold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.group.isNotBlank()) Text(text = item.group, color = IptvTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isCurrentChannel) {
                Box(modifier = Modifier.background(IptvLive, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("▶", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // ── Media card ────────────────────────────────────────────────────────────

    @Composable
    private fun MediaCard(item: CatalogItem, onFocused: () -> Unit, onClick: () -> Unit) {
        var isFocused by remember { mutableStateOf(false) }
        val isChannelOrEvent = item.kind == ContentKind.CHANNEL || item.kind == ContentKind.EVENT
        val cardWidth = if (isChannelOrEvent) 190.dp else 140.dp
        val imageHeight = if (isChannelOrEvent) 107.dp else 200.dp

        Column(
            modifier = Modifier
                .width(cardWidth)
                .background(if (isFocused) IptvFocusBg else IptvCard, RoundedCornerShape(10.dp))
                .border(if (isFocused) 2.dp else 1.dp, if (isFocused) IptvFocusBorder else IptvSurfaceVariant, RoundedCornerShape(10.dp))
                .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocused() }
                .clickable { onClick() },
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(imageHeight).background(IptvSurfaceVariant, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    item.kind == ContentKind.EVENT -> EventSportPlaceholder(item)
                    item.imageUrl.isNotBlank() -> RemoteImage(url = item.imageUrl, width = 300, height = 400, scaleType = if (item.kind == ContentKind.CHANNEL) ScaleType.FIT_CENTER else ScaleType.CENTER_CROP)
                    else -> PlaceholderIcon(kind = item.kind)
                }
                item.badgeText.takeIf { it.isNotBlank() && it !in REDUNDANT_BADGES && item.kind != ContentKind.CHANNEL }?.let { badge ->
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(10.dp).background(if (item.kind == ContentKind.EVENT) IptvLive else IptvSurface, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(badge, color = IptvTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (item.isWatched) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = "Visto",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "VISTO",
                            color = Color(0xFF4CAF50),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            val isVod = item.kind == ContentKind.MOVIE || item.kind == ContentKind.SERIES
            Column(
                modifier = Modifier.fillMaxWidth().then(if (isChannelOrEvent) Modifier.height(78.dp) else Modifier.height(64.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = if (isVod) Arrangement.Center else Arrangement.Top,
            ) {
            val displayTitle = when {
                    item.kind == ContentKind.SERIES && !item.seriesName.isNullOrBlank() -> item.seriesName
                    else -> item.normalizedTitle?.takeUnless { it.equals("null", ignoreCase = true) }?.takeIf { it.isNotBlank() } ?: item.title.takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
                }
                Text(displayTitle, color = IptvTextPrimary, fontSize = if (isChannelOrEvent) 15.sp else 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!isVod) {
                    Spacer(Modifier.weight(1f))
                    val rawSubtitle = item.subtitle
                    val displaySubtitle = if (item.badgeText.isNotBlank() && rawSubtitle.contains(item.badgeText)) rawSubtitle.replace(item.badgeText, "").replace("•", "").trim() else rawSubtitle
                    Text(displaySubtitle.ifBlank { item.group.ifBlank { kindLabel(item.kind) } }, color = IptvTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    // ── Detail panel ──────────────────────────────────────────────────────────

    @Composable
    private fun DetailPanel(item: CatalogItem, modifier: Modifier = Modifier, primaryActionLabel: String, onPrimaryAction: () -> Unit) {
        Column(
            modifier = modifier.background(IptvSurface, RoundedCornerShape(10.dp)).border(1.dp, IptvSurfaceVariant, RoundedCornerShape(10.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(modifier = Modifier.width(if (item.kind == ContentKind.CHANNEL) 220.dp else 260.dp).height(if (item.kind == ContentKind.CHANNEL) 140.dp else 320.dp).background(IptvSurfaceVariant, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    when {
                        item.kind == ContentKind.EVENT -> EventSportPlaceholder(item, emojiSize = 72.sp)
                        item.imageUrl.isNotBlank() -> RemoteImage(url = item.imageUrl, width = 360, height = 480, scaleType = if (item.kind == ContentKind.CHANNEL) ScaleType.FIT_CENTER else ScaleType.CENTER_CROP)
                        else -> PlaceholderIcon(kind = item.kind, size = 48.dp)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(item.title, color = IptvTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOf(item.group.ifBlank { null }, item.subtitle.ifBlank { null }, kindLabel(item.kind)).filterNotNull().joinToString("  •  "), color = IptvTextMuted, fontSize = 16.sp)
                    item.badgeText.takeIf { it.isNotBlank() && it !in REDUNDANT_BADGES }?.let { badge ->
                        Box(modifier = Modifier.background(if (item.kind == ContentKind.EVENT) IptvLive else IptvCard, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(badge, color = IptvTextPrimary, fontSize = 12.sp) }
                    }
                    Text(item.description.ifBlank { "Listo para reproducir" }, color = IptvTextPrimary, fontSize = 16.sp, maxLines = 7, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    FocusButton(label = primaryActionLabel, icon = Icons.Outlined.PlayArrow, onClick = onPrimaryAction)
                }
            }
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    @Composable
    private fun SettingsContent() {
        var preferredLanguage by remember { mutableStateOf(PreferencesManager.getPreferredLanguageOrDefault()) }
        var showLanguageDialog by remember { mutableStateOf(false) }
        var showChangelogDialog by remember { mutableStateOf(false) }
        val languageFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        val availableLanguages = listOf("ES" to "Español", "EN" to "Inglés")
        val installedVersionLabel = installedAppVersion?.let { "${it.versionName} (${it.versionCode})" } ?: "Desconocida"
        val hasUpdate = availableUpdate?.let { evaluateAppUpdate(installedAppVersion ?: InstalledAppVersion("0", 0), it) != AppUpdateAvailability.UP_TO_DATE } == true
        val updateActionLabel = when { isUpdateDownloading -> "Descargando..."; isCheckingUpdates -> "Comprobando..."; hasUpdate -> "Descargar actualización"; else -> "Buscar actualizaciones" }

        Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            ScreenHeader(title = "Ajustes", subtitle = "Actualizaciones, idioma y sesion")
            Column(modifier = Modifier.width(760.dp).background(IptvSurface, RoundedCornerShape(10.dp)).border(1.dp, IptvSurfaceVariant, RoundedCornerShape(10.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                val selectedLanguageLabel = availableLanguages.find { it.first == preferredLanguage }?.second ?: "Español"
                SettingsRowClickable(label = "Idioma de series", value = selectedLanguageLabel) { showLanguageDialog = true }
                SettingsRow("Version de la app", installedVersionLabel)
                SettingsRow("Canales cargados", channelLineup.size.toString())
                SettingsRow("Contenido indexado", searchableItems.size.toString())
                updateErrorMessage?.let { Text(it, color = IptvLive, fontSize = 14.sp) }
                val update = availableUpdate
                if (hasUpdate && update != null) Text("Ultima version: v${update.latestVersionName}", color = IptvAccent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                else if (update != null) Text("Actualizado a la ultima version", color = IptvOnline, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                val changelogText = update?.changelog
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!changelogText.isNullOrBlank()) {
                        FocusButton(label = "Ver novedades", icon = Icons.Outlined.Info, modifier = Modifier.weight(1f)) { showChangelogDialog = true }
                    }
                    FocusButton(label = updateActionLabel, icon = Icons.Outlined.PlayArrow, modifier = Modifier.weight(1f)) {
                        if (!isUpdateDownloading && !isCheckingUpdates) {
                            scope.launch {
                                isCheckingUpdates = true
                                val remoteUpdate = runCatching { appUpdateRepository.fetchRemoteUpdate() }.getOrNull()
                                if (remoteUpdate != null) { appUpdateRepository.cacheUpdate(remoteUpdate); availableUpdate = remoteUpdate }
                                val installed = installedAppVersion
                                val latest = remoteUpdate ?: availableUpdate
                                isCheckingUpdates = false
                                if (latest == null || installed == null || evaluateAppUpdate(installed, latest) == AppUpdateAvailability.UP_TO_DATE) {
                                    Toast.makeText(requireContext(), "Ya tienes la última versión instalada", Toast.LENGTH_SHORT).show()
                                } else {
                                    startUpdateFlow()
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FocusButton(label = "Cerrar sesion", icon = Icons.Outlined.Settings) { performSignOut() }
                }
            }
        }

        if (showLanguageDialog) {
            FilterDialog(
                title = "Idioma preferido",
                options = availableLanguages.map { CatalogFilterOption(value = it.first, label = it.second) },
                selectedOption = preferredLanguage,
                onOptionSelected = { PreferencesManager.preferredLanguage = it.value; preferredLanguage = it.value; showLanguageDialog = false },
                onDismiss = { showLanguageDialog = false; runCatching { languageFocusRequester.requestFocus() } },
            )
        }

        if (showChangelogDialog) {
            composeDialogOpen = true
            val update = availableUpdate ?: mandatoryUpdate
            if (update != null) {
                ChangelogDialog(versionName = update.latestVersionName, markdown = update.changelog.ifBlank { "Sin notas de la version." }, onDismiss = { showChangelogDialog = false; composeDialogOpen = false })
            } else {
                val installed = installedAppVersion
                ChangelogDialog(versionName = installed?.versionName ?: "Desconocida", markdown = "No hay informacion de actualizacion disponible en este momento.", onDismiss = { showChangelogDialog = false; composeDialogOpen = false })
            }
        }
    }

    @Composable
    private fun SettingsRowClickable(label: String, value: String, onClick: () -> Unit) {
        var isFocused by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth().background(if (isFocused) IptvFocusBg else Color.Transparent, RoundedCornerShape(8.dp)).border(1.dp, if (isFocused) IptvFocusBorder else Color.Transparent, RoundedCornerShape(8.dp)).onFocusChanged { isFocused = it.isFocused }.clickable { onClick() }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = IptvTextPrimary, fontSize = 16.sp)
            Text("$value ▸", color = IptvAccent, fontSize = 16.sp)
        }
    }

    @Composable
    private fun SettingsRow(label: String, value: String) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = IptvTextPrimary, fontSize = 16.sp)
            Text(value, color = IptvTextMuted, fontSize = 16.sp)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Composable
    private fun FocusButton(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
        var isFocused by remember { mutableStateOf(false) }
        Row(
            modifier = modifier.height(52.dp).background(if (isFocused) IptvAccent else IptvCard, RoundedCornerShape(8.dp)).border(if (isFocused) 2.dp else 1.dp, if (isFocused) IptvTextPrimary else IptvSurfaceVariant, RoundedCornerShape(8.dp)).onFocusChanged { isFocused = it.isFocused }.clickable { onClick() }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = label, tint = IptvTextPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = IptvTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }

    @Composable
    private fun PlaceholderIcon(kind: ContentKind, size: androidx.compose.ui.unit.Dp = 32.dp) {
        val icon = when (kind) { ContentKind.EVENT -> Icons.Outlined.Event; ContentKind.CHANNEL -> Icons.Outlined.LiveTv; ContentKind.MOVIE -> Icons.Outlined.Movie; ContentKind.SERIES -> Icons.Outlined.Tv }
        Icon(icon, contentDescription = null, tint = IptvTextMuted, modifier = Modifier.size(size))
    }

    @Composable
    private fun EventSportPlaceholder(item: CatalogItem, emojiSize: TextUnit = 48.sp) {
        val category = item.group.lowercase()
        val text = java.text.Normalizer.normalize(category, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        val emoji = when { text.contains("futbol") -> "⚽"; text.contains("baloncesto") -> "🏀"; text.contains("tenis") -> "🎾"; text.contains("motociclismo") -> "🏍️"; text.contains("automovilismo") -> "🏎️"; text.contains("mma") || text.contains("boxeo") -> "🥊"; text.contains("rugby") -> "🏈"; text.contains("balonmano") -> "🤾"; text.contains("hockey") -> "🏒"; text.contains("padel") -> "🏸"; else -> "🏆" }
        val colors = when { text.contains("futbol") -> listOf(Color(0xFF0B6E4F), Color(0xFF1A936F)); text.contains("baloncesto") -> listOf(Color(0xFF7F4F24), Color(0xFFD68C45)); text.contains("tenis") -> listOf(Color(0xFF254441), Color(0xFF43AA8B)); text.contains("motociclismo") || text.contains("automovilismo") -> listOf(Color(0xFF1D3557), Color(0xFF457B9D)); text.contains("mma") || text.contains("boxeo") -> listOf(Color(0xFF5F0F40), Color(0xFF9A031E)); text.contains("rugby") -> listOf(Color(0xFF4A1942), Color(0xFF893642)); text.contains("balonmano") -> listOf(Color(0xFF1E3A5F), Color(0xFF3D5A80)); text.contains("padel") -> listOf(Color(0xFF2E7D32), Color(0xFF66BB6A)); text.contains("hockey") -> listOf(Color(0xFF37474F), Color(0xFF78909C)); else -> listOf(Color(0xFF102A43), Color(0xFFD64550)) }
        Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
            Text(text = emoji, fontSize = emojiSize, textAlign = TextAlign.Center)
        }
    }

    @Composable
    private fun ScreenHeader(title: String, subtitle: String) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = IptvTextPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) Text(subtitle, color = IptvTextMuted, fontSize = 16.sp)
        }
    }

    @Composable
    private fun RemoteImage(url: String, width: Int, height: Int, scaleType: ScaleType) {
        AndroidView(
            factory = { context -> ImageView(context).apply { this.scaleType = scaleType; setBackgroundColor(AndroidColor.TRANSPARENT) } },
            update = { iv -> iv.scaleType = scaleType; Glide.with(iv.context.applicationContext).load(url).override(width, height).dontTransform().into(iv) },
            onRelease = { iv -> runCatching { Glide.with(iv.context.applicationContext).clear(iv) }.onFailure { Log.w(TAG, "No se pudo limpiar imagen", it) } },
            modifier = Modifier.fillMaxSize(),
        )
    }

    // ── Card / section helpers ────────────────────────────────────────────────

    private fun handleCardClick(item: CatalogItem, lineup: List<CatalogItem> = emptyList()) {
        continueWatchingEntries[item.stableId]?.let { progress ->
            openContinueWatchingItem(item, progress)
            return
        }
        if (item.kind == ContentKind.SERIES && item.seriesName != null) {
            val fragment = SeriesDetailFragment.newInstance(item.seriesName)
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, fragment)
                .addToBackStack("SeriesDetailFragment")
                .commit()
            return
        }
        activePlaybackLineup = lineup.filter { it.kind == item.kind }
        playCatalogItem(item, 0)
    }

    private fun openContinueWatchingItem(cardItem: CatalogItem, progress: WatchProgressItem) {
        scope.launch {
            when (progress.contentType) {
                "movie" -> openContinueWatchingMovie(progress)
                "series" -> openContinueWatchingSeries(cardItem, progress)
                else -> Log.w(TAG, "Unsupported continue watching type: ${progress.contentType}")
            }
        }
    }

    private suspend fun openContinueWatchingMovie(progress: WatchProgressItem) {
        val item = repository.fetchContentItem(ContentKind.MOVIE, progress.contentId)
        if (item == null) {
            Log.w(TAG, "CW movie unresolved: ${progress.contentId}")
            withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "No se pudo abrir la pelicula", Toast.LENGTH_SHORT).show() }
            return
        }
        withContext(Dispatchers.Main) { activePlaybackLineup = emptyList(); playResolvedCatalogItem(item, 0) }
    }

    private suspend fun openContinueWatchingSeries(cardItem: CatalogItem, progress: WatchProgressItem) {
        val episode = repository.fetchContentItem(ContentKind.SERIES, progress.contentId)
        if (episode == null) {
            Log.w(TAG, "CW series episode unresolved: ${progress.contentId}")
            withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "No se pudo abrir la serie", Toast.LENGTH_SHORT).show() }
            return
        }
        val seriesName = episode.seriesName ?: progress.seriesName ?: cardItem.seriesName ?: cardItem.title
        val allEpisodes = repository.loadSeriesEpisodes(seriesName)
        val logicalEpisodes = allEpisodes
            .groupBy { Triple(it.seriesName, it.seasonNumber, it.episodeNumber) }
            .values
            .mapNotNull { variants ->
                variants.firstOrNull { normalizeLanguageCode(it.idioma) == normalizeLanguageCode(PreferencesManager.getPreferredLanguageOrDefault()) } ?: variants.firstOrNull()
            }
            .sortedWith(compareBy<CatalogItem>({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }))

        val nextEpisodeCallback: (() -> Unit)? = logicalEpisodes.indexOfFirst {
            it.seriesName == episode.seriesName && it.seasonNumber == episode.seasonNumber && it.episodeNumber == episode.episodeNumber
        }.takeIf { it >= 0 && it < logicalEpisodes.lastIndex }?.let { currentIndex ->
            { openContinueWatchingItem(cardItem, progress.copy(contentId = logicalEpisodes[currentIndex + 1].providerId ?: logicalEpisodes[currentIndex + 1].stableId, seasonNumber = logicalEpisodes[currentIndex + 1].seasonNumber, episodeNumber = logicalEpisodes[currentIndex + 1].episodeNumber, seriesName = logicalEpisodes[currentIndex + 1].seriesName, title = logicalEpisodes[currentIndex + 1].title, imageUrl = logicalEpisodes[currentIndex + 1].imageUrl)) }
        }

        val previousEpisodeCallback: (() -> Unit)? = logicalEpisodes.indexOfFirst {
            it.seriesName == episode.seriesName && it.seasonNumber == episode.seasonNumber && it.episodeNumber == episode.episodeNumber
        }.takeIf { it > 0 }?.let { currentIndex ->
            { openContinueWatchingItem(cardItem, progress.copy(contentId = logicalEpisodes[currentIndex - 1].providerId ?: logicalEpisodes[currentIndex - 1].stableId, seasonNumber = logicalEpisodes[currentIndex - 1].seasonNumber, episodeNumber = logicalEpisodes[currentIndex - 1].episodeNumber, seriesName = logicalEpisodes[currentIndex - 1].seriesName, title = logicalEpisodes[currentIndex - 1].title, imageUrl = logicalEpisodes[currentIndex - 1].imageUrl)) }
        }

        val stream = episode.streamOptions.firstOrNull()
        if (stream == null) {
            withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "No hay streams disponibles", Toast.LENGTH_SHORT).show() }
            return
        }

        withContext(Dispatchers.Main) {
            val playerFragment = PlayerFragment(
                streamUrl = stream.url, overlayNumber = episode.kind.name, overlayTitle = episode.title,
                overlayMeta = episode.description.ifBlank { stream.label }, contentKind = episode.kind,
                onNavigateChannel = { false }, onNavigateOption = { false }, onDirectChannelNumber = { false },
                onToggleFavorite = { false }, onOpenFavorites = { false }, onOpenRecents = { false },
                onNextEpisode = nextEpisodeCallback,
                onPreviousEpisode = previousEpisodeCallback,
                allSeriesEpisodes = allEpisodes, currentEpisode = episode,
                overlayLogoUrl = episode.imageUrl, contentId = episode.providerId ?: progress.contentId,
                onPlayerClosed = { restorePlaybackReturnState(); restoreFocusAfterPlayer() },
            )
            rememberPlaybackReturnState(cardItem)
            currentItem = cardItem
            currentStreamIndex = 0
            val fm = requireActivity().supportFragmentManager
            fm.findFragmentById(R.id.player_container)?.let { fm.beginTransaction().remove(it).commitNow() }
            fm.beginTransaction().replace(R.id.player_container, playerFragment, "player_fragment").commitNow()
            val container = requireActivity().findViewById<FrameLayout>(R.id.player_container)
            container.visibility = View.VISIBLE
            container.isFocusable = true
            container.isFocusableInTouchMode = true
            runCatching { container.requestFocus() }
        }
    }

    private fun refreshCatalog() {
        homeCatalog = null; homeSections = emptyList(); continueWatchingSection = null; continueWatchingEntries = emptyMap()
        searchableItems = emptyList(); channelLineup = emptyList(); activePlaybackLineup = emptyList(); selectedHero = null; isLoaded = false
        repository.clearHomeMemoryCache()
        startLoad(forceRefresh = true)
    }

    private fun buildDisplaySections(baseSections: List<BrowseSection>, allItems: List<CatalogItem>): List<BrowseSection> {
        return baseSections
    }

    private fun buildFavoriteSection(remoteFavoriteItems: List<CatalogItem>?, channels: List<CatalogItem>): BrowseSection? {
        val favoriteIds = channelStateStore.favoriteIds().ifEmpty { remoteFavoriteItems.orEmpty().map(CatalogItem::stableId).toSet() }
        if (favoriteIds.isEmpty()) return null
        val channelById = (channels + CatalogMemory.channelRegistry.values).associateBy(CatalogItem::stableId)
        val allFavoriteChannels = favoriteIds.mapNotNull(channelById::get)
        val remoteOrder = remoteFavoriteItems.orEmpty().mapNotNull { channelById[it.stableId] }.filter { favoriteIds.contains(it.stableId) }
        val items = if (remoteOrder.isNotEmpty()) {
            val orderedIds = remoteOrder.map(CatalogItem::stableId).toSet()
            remoteOrder + allFavoriteChannels.filterNot { orderedIds.contains(it.stableId) }
        } else allFavoriteChannels
        return items.takeIf { it.isNotEmpty() }?.let { BrowseSection("Favoritos", it) }
    }

    private fun buildRecentSection(channels: List<CatalogItem>): BrowseSection? {
        val byId = channels.associateBy(CatalogItem::stableId)
        val items = channelStateStore.recentIds().mapNotNull(byId::get)
        return items.takeIf { it.isNotEmpty() }?.let { BrowseSection("Ultimos canales", it) }
    }

    private fun buildTypeSections(title: String, items: List<CatalogItem>, mainLimit: Int): List<BrowseSection> {
        if (items.isEmpty()) return emptyList()
        return buildList {
            add(BrowseSection(title, items.take(mainLimit)))
            items.groupBy { it.group.ifBlank { "Sin categoria" } }
                .entries.sortedWith(compareByDescending<Map.Entry<String, List<CatalogItem>>> { it.value.size }.thenBy { it.key })
                .take(3).forEach { add(BrowseSection(it.key, it.value.take(14))) }
        }
    }

    private fun screenTitle(kind: ContentKind) = when (kind) { ContentKind.EVENT -> "Eventos"; ContentKind.CHANNEL -> "TV en directo"; ContentKind.MOVIE -> "Peliculas"; ContentKind.SERIES -> "Series" }
    private fun kindLabel(kind: ContentKind) = when (kind) { ContentKind.EVENT -> "Evento"; ContentKind.CHANNEL -> "Canal"; ContentKind.MOVIE -> "Pelicula"; ContentKind.SERIES -> "Serie" }
    private fun sectionKindLabel(items: List<CatalogItem>): String {
        val kind = items.firstOrNull()?.kind ?: return ""
        val count = items.size
        val (singular, plural) = when (kind) { ContentKind.CHANNEL -> "canal" to "canales"; ContentKind.EVENT -> "evento" to "eventos"; ContentKind.MOVIE -> "pelicula" to "peliculas"; ContentKind.SERIES -> "serie" to "series" }
        return if (count == 1) "1 $singular" else "$count $plural"
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun playCatalogItem(item: CatalogItem, optionIndex: Int) {
        if (item.kind == ContentKind.EVENT) {
            scope.launch {
                val resolved = repository.resolveEventItem(item)
                if (resolved.streamOptions.isEmpty()) { Toast.makeText(requireContext(), R.string.no_streams_available, Toast.LENGTH_SHORT).show(); return@launch }
                playResolvedCatalogItem(resolved, optionIndex.coerceIn(resolved.streamOptions.indices))
            }
            return
        }
        playResolvedCatalogItem(item, optionIndex)
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun playResolvedCatalogItem(item: CatalogItem, optionIndex: Int) {
        val stream = item.streamOptions.getOrNull(optionIndex) ?: return
        rememberPlaybackReturnState(item)
        currentItem = item
        currentStreamIndex = optionIndex
        if (item.kind == ContentKind.CHANNEL) channelStateStore.markRecent(item)
        val fm = requireActivity().supportFragmentManager
        fm.findFragmentById(R.id.player_container)?.let { fm.beginTransaction().remove(it).commitNow() }
        val channelItem = resolveChannelFromEvent(item, stream)
        val favoriteTarget = channelItem ?: item
        val playerFragment = PlayerFragment(
            streamUrl = stream.url,
            overlayNumber = when { item.kind == ContentKind.CHANNEL && item.channelNumber != null -> "CH ${item.channelNumber}"; item.kind == ContentKind.EVENT -> "EN DIRECTO"; else -> item.kind.name },
            overlayTitle = item.title,
            overlayMeta = listOf(item.subtitle, stream.label).filter { it.isNotBlank() }.joinToString("  •  ").ifBlank { item.description },
            contentKind = item.kind,
            onNavigateChannel = ::navigateChannel,
            onNavigateOption = ::navigateOption,
            onDirectChannelNumber = ::navigateToChannelNumber,
            onToggleFavorite = { toggleFavorite(favoriteTarget) },
            onOpenFavorites = ::openFavoriteChannel,
            onOpenRecents = ::openRecentChannel,
            onOpenGuide = { showChannelPicker = true },
            streamOptionLabels = item.streamOptions.map { it.label },
            currentOptionIndex = optionIndex,
            onSelectQuality = if (item.kind == ContentKind.MOVIE || item.kind == ContentKind.SERIES) { newIndex -> playCatalogItem(item, newIndex) } else null,
            overlayLogoUrl = item.imageUrl,
            isFavorite = channelStateStore.isFavorite(favoriteTarget),
            contentId = item.providerId ?: item.stableId,
            onPlayerClosed = { restorePlaybackReturnState(); restoreFocusAfterPlayer() },
            customHeaders = stream.headers,
        )
        fm.beginTransaction().replace(R.id.player_container, playerFragment, "player_fragment").commitNow()
        val container = requireActivity().findViewById<FrameLayout>(R.id.player_container)
        container.visibility = View.VISIBLE
        container.isFocusable = true
        container.isFocusableInTouchMode = true
        runCatching { container.requestFocus() }
    }

    private fun navigateChannel(direction: Int) {
        val current = currentItem ?: return
        val lineup = activePlaybackLineup.ifEmpty { channelLineup }
        if (lineup.isEmpty()) return
        val idx = lineup.indexOfFirst { it.stableId == current.stableId }.takeIf { it != -1 } ?: return
        val target = idx + direction
        if (target !in lineup.indices) return
        playCatalogItem(lineup[target], 0)
    }

    private fun navigateOption(direction: Int) {
        val item = currentItem ?: return
        val newIndex = currentStreamIndex + direction
        if (newIndex !in item.streamOptions.indices) return
        playCatalogItem(item, newIndex)
    }

    private fun navigateToChannelNumber(number: Int): Boolean {
        val match = channelLineup.firstOrNull { it.channelNumber == number } ?: return false
        playCatalogItem(match, 0)
        return true
    }

    private fun toggleFavorite(item: CatalogItem): Boolean {
        CatalogMemory.registerChannel(item)
        val result = channelStateStore.toggleFavorite(item)
        rebuildHomeSections()
        scope.launch {
            runCatching { repository.updateChannelFavorite(item, result) }
                .onFailure {
                    Log.e(TAG, "No se pudo actualizar favorito ${item.stableId}", it)
                    channelStateStore.setFavorite(item, !result)
                    rebuildHomeSections()
                    Toast.makeText(requireContext(), "No se pudo actualizar favoritos", Toast.LENGTH_SHORT).show()
                }
        }
        return result
    }

    private fun resolveChannelFromEvent(item: CatalogItem, stream: StreamOption?): CatalogItem? {
        if (item.kind != ContentKind.EVENT) return null
        val providerId = stream?.providerId
        if (providerId.isNullOrBlank()) return null
        val stableId = "channel:$providerId"
        val resolved = channelLineup.firstOrNull { it.stableId == stableId } ?: CatalogMemory.channelRegistry[stableId]
        if (resolved != null) return resolved
        val channelName = stream?.label?.split(" · ")?.firstOrNull() ?: item.description.split(" · ").firstOrNull() ?: item.title
        val fallback = CatalogItem(stableId = stableId, providerId = providerId, title = channelName, subtitle = "", description = item.description, imageUrl = item.imageUrl, kind = ContentKind.CHANNEL, group = item.group, badgeText = item.badgeText, streamOptions = item.streamOptions)
        CatalogMemory.registerChannel(fallback)
        return fallback
    }

    private fun openFavoriteChannel(): Boolean {
        val ids = channelStateStore.favoriteIds()
        val match = channelLineup.firstOrNull { ids.contains(it.stableId) } ?: return false
        playCatalogItem(match, 0)
        return true
    }

    private fun openRecentChannel(): Boolean {
        val byId = channelLineup.associateBy(CatalogItem::stableId)
        val match = channelStateStore.recentIds().drop(1).mapNotNull(byId::get).firstOrNull()
            ?: channelStateStore.recentIds().mapNotNull(byId::get).firstOrNull() ?: return false
        playCatalogItem(match, 0)
        return true
    }

    private fun openGuideOverlay(initialGroup: String?) {
        Log.d(TAG, "openGuideOverlay CALLED initialGroup=$initialGroup currentMode=$currentMode")
        val fm = requireActivity().supportFragmentManager
        val playerFragment = fm.findFragmentByTag("player_fragment") as? PlayerFragment
        playerFragment?.closeFromHost()
        playerFragment?.let { fm.beginTransaction().remove(it).commitNow() }
        val container = requireActivity().findViewById<FrameLayout>(R.id.player_container)
        container?.visibility = View.GONE
        if (initialGroup != null) guideInitialGroup = initialGroup
        scope.launch {
            ensureFiltersLoadedAwait(ContentKind.CHANNEL)
            currentMode = MainMode.TV
            selectedHero = defaultItemForMode(MainMode.TV)
            restoreFocusAfterPlayer()
        }
    }

    private fun isLikelyLiveNow(item: CatalogItem): Boolean {
        if (item.kind != ContentKind.EVENT) return false
        val parsed = runCatching { EVENT_TIME_FORMAT.parse(item.badgeText) }.getOrNull() ?: return false
        val now = Calendar.getInstance()
        val eventCal = Calendar.getInstance().apply {
            time = parsed
            set(Calendar.YEAR, now.get(Calendar.YEAR))
            set(Calendar.MONTH, now.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
        }
        val delta = (now.timeInMillis - eventCal.timeInMillis) / 60_000L
        return delta in -20..180
    }

    private fun findNextEventIndex(items: List<CatalogItem>): Int {
        val now = Calendar.getInstance()
        var bestUpcoming = -1; var bestUpcomingDelta = Long.MAX_VALUE
        var bestLive = -1; var bestLiveDelta = Long.MAX_VALUE
        for (i in items.indices) {
            if (items[i].kind != ContentKind.EVENT) continue
            val parsed = runCatching { EVENT_TIME_FORMAT.parse(items[i].badgeText) }.getOrNull() ?: continue
            val eventCal = Calendar.getInstance().apply {
                time = parsed
                set(Calendar.YEAR, now.get(Calendar.YEAR)); set(Calendar.MONTH, now.get(Calendar.MONTH)); set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
            }
            val delta = (now.timeInMillis - eventCal.timeInMillis) / 60_000L
            when {
                delta < 0 && -delta < bestUpcomingDelta -> { bestUpcoming = i; bestUpcomingDelta = -delta }
                delta in 0..180 && delta < bestLiveDelta -> { bestLive = i; bestLiveDelta = delta }
            }
        }
        return if (bestUpcoming >= 0) bestUpcoming else bestLive
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private enum class MainMode { Home, TV, Movies, Series, Events, Anime, Settings }

    private data class NavItem(
        val icon: ImageVector,
        val label: String,
        val mode: MainMode? = null,
        val activatesOnFocus: Boolean = true,
        val onClick: (() -> Unit)? = null,
    )

    private data class PlaybackReturnState(
        val mode: MainMode,
        val selectedItemStableId: String,
    )

    // ── Anime Browse ─────────────────────────────────────────────────────────

    private enum class AnimeTab { ON_AIR, LATEST, SEARCH }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun showAnimeEpisodeServers(
        slug: String,
        episodeNumber: Int,
        isLoading: Boolean,
        setLoading: (Boolean) -> Unit,
        scope: CoroutineScope,
    ) {
        scope.launch {
            setLoading(true)
            AnimeFlvApiClient.getEpisodeServers(slug, episodeNumber)
                .onSuccess { episodeDetail ->
                    val availableServers = episodeDetail.servers.filter { it.embed?.isNotBlank() == true || it.download?.isNotBlank() == true }
                    if (availableServers.isEmpty()) {
                        Toast.makeText(requireContext(), "No hay servidores disponibles", Toast.LENGTH_SHORT).show()
                        return@onSuccess
                    }

                    // Show server selection dialog
                    val serverNames = availableServers.map { it.name }.toTypedArray()

                    withContext(Dispatchers.Main) {
                        val dialog = android.app.AlertDialog.Builder(requireContext())
                            .setTitle("${episodeDetail.title} - Episodio $episodeNumber")
                            .setItems(serverNames) { _, which ->
                                val selectedServer = availableServers[which]
                                launchAnimePlayer(selectedServer, episodeDetail.title, episodeNumber, availableServers)
                            }
                            .setNegativeButton("Cancelar", null)
                            .create()
                        dialog.show()
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error al obtener episodio", Toast.LENGTH_SHORT).show()
                    }
                }
            setLoading(false)
        }
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun launchAnimePlayer(
        server: AnimeServer,
        animeTitle: String,
        episodeNumber: Int,
        allServers: List<AnimeServer>,
    ) {
        val embedUrl = server.embed ?: server.download ?: run {
            Toast.makeText(requireContext(), "Sin URL disponible", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Extrayendo video...", Toast.LENGTH_SHORT).show()
            }

            val token = runCatching { repository.getAccessToken() }.getOrNull()
            if (token != null) {
                VideoExtractorApiClient.authToken = token
            }

            val result = VideoExtractorApiClient.extract(embedUrl)

            withContext(Dispatchers.Main) {
                result.onSuccess { extractResult ->
                    Log.d(TAG, "VideoExtractResult: url=${extractResult.url.take(50)}..., provider=${extractResult.provider}, headers=${extractResult.headers}")
                    val item = CatalogItem(
                        stableId = "anime:$animeTitle:ep$episodeNumber",
                        title = animeTitle,
                        subtitle = "Episodio $episodeNumber [${extractResult.provider}]",
                        description = "Anime - Episodio $episodeNumber - ${extractResult.type}",
                        imageUrl = "",
                        kind = ContentKind.SERIES,
                        group = "Anime",
                        badgeText = "Anime",
                        seriesName = animeTitle,
                        episodeNumber = episodeNumber,
                        streamOptions = listOf(StreamOption(extractResult.provider, extractResult.url, headers = extractResult.headers)),
                    )
                    playResolvedCatalogItem(item, 0)
                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        "Error: ${error.message ?: "No se pudo extraer el video"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    @Composable
    private fun AnimeBrowseContent() {
        var currentTab by remember { mutableStateOf(AnimeTab.ON_AIR) }
        var onAirItems by remember { mutableStateOf<List<AnimeOnAir>>(emptyList()) }
        var latestItems by remember { mutableStateOf<List<LatestEpisode>>(emptyList()) }
        var searchQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<AnimeMedia>>(emptyList()) }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var detailSlug by remember { mutableStateOf<String?>(null) }

        val animeScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            isLoading = true
            AnimeFlvApiClient.getAnimesOnAir()
                .onSuccess { onAirItems = it }
                .onFailure { Log.e(TAG, "Error loading animes on air", it) }
            isLoading = false
        }

        if (detailSlug != null) {
            AnimeDetailContent(
                slug = detailSlug!!,
                onBack = { detailSlug = null },
                onPlayEpisode = { slug, episodeNumber ->
                    showAnimeEpisodeServers(slug, episodeNumber, isLoading, { isLoading = it }, animeScope)
                },
            )
            return
        }

        Column(modifier = Modifier.fillMaxSize().background(IptvBackground)) {
            // Tab bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnimeTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    val label = when (tab) {
                        AnimeTab.ON_AIR -> "En emision"
                        AnimeTab.LATEST -> "Ultimos"
                        AnimeTab.SEARCH -> "Buscar"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) IptvAccent else IptvSurface)
                            .border(1.dp, if (isSelected) IptvFocusBorder else IptvSurfaceVariant, RoundedCornerShape(8.dp))
                            .clickable { currentTab = tab }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) IptvTextPrimary else IptvTextMuted,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            when (currentTab) {
                AnimeTab.ON_AIR -> AnimeGridContent(onAirItems, isLoading) { anime ->
                    detailSlug = anime.slug
                }
                AnimeTab.LATEST -> AnimeGridLatestContent(latestItems, isLoading) { ep ->
                    val slug = ep.slug.substringBeforeLast("-")
                    showAnimeEpisodeServers(slug, ep.number, isLoading, { isLoading = it }, animeScope)
                }
                AnimeTab.SEARCH -> AnimeSearchContent(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {
                        animeScope.launch {
                            isLoading = true
                            AnimeFlvApiClient.search(searchQuery)
                                .onSuccess { searchResults = it.media }
                                .onFailure { errorMessage = it.message }
                            isLoading = false
                        }
                    },
                    results = searchResults,
                    onAnimeClick = { media -> detailSlug = media.slug },
                )
            }

            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    @Composable
    private fun AnimeGridContent(
        items: List<AnimeOnAir>,
        isLoading: Boolean,
        onClick: (AnimeOnAir) -> Unit,
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando...", color = IptvTextMuted, fontSize = 16.sp)
            }
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items) { anime ->
                AnimeCard(title = anime.title, type = anime.type) { onClick(anime) }
            }
        }
    }

    @Composable
    private fun AnimeGridLatestContent(
        items: List<LatestEpisode>,
        isLoading: Boolean,
        onClick: (LatestEpisode) -> Unit,
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando...", color = IptvTextMuted, fontSize = 16.sp)
            }
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items) { ep ->
                AnimeCard(title = "${ep.title} - Ep. ${ep.number}", type = "Episodio", coverUrl = ep.cover) { onClick(ep) }
            }
        }
    }

    @Composable
    private fun AnimeSearchContent(
        query: String,
        onQueryChange: (String) -> Unit,
        onSearch: () -> Unit,
        results: List<AnimeMedia>,
        onAnimeClick: (AnimeMedia) -> Unit,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(IptvSurface)
                        .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        textStyle = TextStyle(color = IptvTextPrimary, fontSize = 14.sp),
                        modifier = Modifier.fillMaxWidth().onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                            ) {
                                onSearch()
                                true
                            } else false
                        },
                        singleLine = true,
                    )
                    if (query.isEmpty()) {
                        Text("Buscar anime...", color = IptvTextMuted, fontSize = 14.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(IptvAccent)
                        .clickable { onSearch() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = Color.White)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(results) { media ->
                    AnimeCard(title = media.title, type = media.type, coverUrl = media.cover) { onAnimeClick(media) }
                }
            }
        }
    }

    @Composable
    private fun AnimeCard(
        title: String,
        type: String,
        coverUrl: String? = null,
        onClick: () -> Unit,
    ) {
        var isFocused by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isFocused) IptvFocusBg else IptvCard)
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) IptvFocusBorder else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onClick)
                .onFocusChanged { isFocused = it.hasFocus }
                .padding(12.dp)
                .focusable(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(IptvSurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    AndroidView(
                        factory = { ctx -> ImageView(ctx) },
                        update = { imageView ->
                            Glide.with(imageView)
                                .load(coverUrl)
                                .centerCrop()
                                .into(imageView)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = title.take(2).uppercase(),
                        color = IptvTextMuted,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                color = IptvTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (type.isNotBlank()) {
                Text(
                    text = type.uppercase(),
                    color = IptvTextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    // ── Anime Detail ─────────────────────────────────────────────────────────

    @Composable
    private fun AnimeDetailContent(
        slug: String,
        onBack: () -> Unit,
        onPlayEpisode: (slug: String, episodeNumber: Int) -> Unit,
    ) {
        var animeDetail by remember { mutableStateOf<AnimeDetail?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val animeScope = rememberCoroutineScope()

        LaunchedEffect(slug) {
            isLoading = true
            AnimeFlvApiClient.getAnimeDetail(slug)
                .onSuccess { animeDetail = it }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }

        Column(modifier = Modifier.fillMaxSize().background(IptvBackground)) {
            // Header with back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(IptvSurface)
                        .clickable { onBack() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("< Volver", color = IptvTextPrimary, fontSize = 14.sp)
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Cargando...", color = IptvTextMuted, fontSize = 16.sp)
                }
                return
            }

            val detail = animeDetail
            if (detail == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = errorMessage ?: "No se encontro el anime",
                        color = Color.Red,
                        fontSize = 16.sp,
                    )
                }
                return
            }

            // Anime info header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Cover
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(IptvSurfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (detail.cover.isNotBlank()) {
                        AndroidView(
                            factory = { ctx -> ImageView(ctx) },
                            update = { imageView ->
                                Glide.with(imageView)
                                    .load(detail.cover)
                                    .centerCrop()
                                    .into(imageView)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail.title,
                        color = IptvTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = detail.type.uppercase(),
                            color = IptvAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("•", color = IptvTextMuted, fontSize = 12.sp)
                        Text(
                            text = detail.status,
                            color = IptvTextMuted,
                            fontSize = 12.sp,
                        )
                        Text("•", color = IptvTextMuted, fontSize = 12.sp)
                        Text(
                            text = "Rating: ${detail.rating}",
                            color = IptvTextMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = detail.genres.joinToString(", "),
                        color = IptvTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.synopsis.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = detail.synopsis,
                            color = IptvTextMuted,
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Episodes list
            if (detail.episodes.isNotEmpty()) {
                Text(
                    text = "Episodios (${detail.episodes.size})",
                    color = IptvTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(detail.episodes.sortedByDescending { it.number }) { episode ->
                        var epFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (epFocused) IptvAccent else IptvSurface)
                                .border(
                                    width = if (epFocused) 2.dp else 0.dp,
                                    color = if (epFocused) IptvFocusBorder else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { onPlayEpisode(slug, episode.number) }
                                .onFocusChanged { epFocused = it.hasFocus }
                                .focusable(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Ep. ${episode.number}",
                                color = if (epFocused) Color.White else IptvTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ComposeMainFragment"
        private const val ALL_OPTION = "Todos"
        private const val FAVORITES_FILTER = "Favoritos"
        private const val PAGINATION_PREFETCH_DISTANCE = 5
        private val EVENT_TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val REDUNDANT_BADGES = setOf("CINE", "SERIE", "Pelicula", "Serie")
    }
}