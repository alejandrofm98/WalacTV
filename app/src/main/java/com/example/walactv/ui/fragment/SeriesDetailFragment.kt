@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.example.walactv.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageView.ScaleType.CENTER_CROP
import com.example.walactv.R
import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.model.StreamOption
import com.example.walactv.WalacApp
import com.example.walactv.data.model.bestTorrentFirst
import com.example.walactv.data.model.idioma
import com.example.walactv.data.model.toUnifiedOptions
import com.example.walactv.data.model.uniqueSeriesEpisodes
import com.example.walactv.data.preferences.PreferencesManager
import com.example.walactv.data.remote.api.dto.PlaybackPreferenceDto
import com.example.walactv.data.remote.api.dto.WatchProgressDto
import com.example.walactv.data.remote.api.dto.isCompleted
import com.example.walactv.data.remote.api.dto.progressPercent
import com.example.walactv.data.remote.repository.IptvRepository
import com.example.walactv.data.remote.torrent.TorrentioClient
import com.example.walactv.data.util.buildSeriesEpisodeProgressMap
import com.example.walactv.data.util.normalizeLanguageCode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.walactv.ui.compose.LONG_PRESS_THRESHOLD_MS
import com.example.walactv.ui.compose.WatchedBadge
import com.example.walactv.ui.compose.buildEpisodeLabel
import com.example.walactv.ui.compose.tvClickable
import com.google.gson.Gson
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.bumptech.glide.Glide
import com.example.walactv.data.model.preferredVodPosterUrl
import com.example.walactv.data.model.playbackContentId
import com.example.walactv.ui.theme.*

class SeriesDetailFragment : Fragment() {
    private lateinit var repository: IptvRepository
    private var detailProgressReloadTrigger by mutableIntStateOf(0)
    private var seriesBackdropUrl: String = ""
    private var seriesPosterUrl: String = ""

    companion object {
        private const val TAG = "SeriesDetailFragment"
        private const val PLAYER_FRAGMENT_TAG = "player_fragment"
        private const val ARG_SERIES_ITEM = "series_item"
        private const val ARG_SERIES_ID = "series_id"
        private const val ARG_INITIAL_SEASON = "initial_season"
        private const val ARG_INITIAL_EPISODE = "initial_episode"
        
        fun newInstance(
            item: CatalogItem,
            seriesId: String? = null,
            initialSeason: Int? = null,
            initialEpisode: Int? = null,
        ) = SeriesDetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SERIES_ITEM, Gson().toJson(item))
                if (!seriesId.isNullOrBlank()) putString(ARG_SERIES_ID, seriesId)
                if (initialSeason != null) putInt(ARG_INITIAL_SEASON, initialSeason)
                if (initialEpisode != null) putInt(ARG_INITIAL_EPISODE, initialEpisode)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = IptvRepository(requireContext())
        Log.d(TAG, "SeriesDetailFragment created for series")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val seriesItemJson = arguments?.getString(ARG_SERIES_ITEM)
        val catalogItem = if (seriesItemJson != null) Gson().fromJson(seriesItemJson, CatalogItem::class.java) else null
        val seriesId = arguments?.getString(ARG_SERIES_ID)
        val initialSeason = arguments?.takeIf { it.containsKey(ARG_INITIAL_SEASON) }
            ?.getInt(ARG_INITIAL_SEASON)
        val initialEpisode = arguments?.takeIf { it.containsKey(ARG_INITIAL_EPISODE) }
            ?.getInt(ARG_INITIAL_EPISODE)
        val seriesName = catalogItem?.seriesName?.ifBlank { null }
            ?: catalogItem?.title
            ?: seriesId
            ?: ""

        // Arte de la serie para la pantalla de carga del player
        seriesBackdropUrl = catalogItem?.backdropUrl.orEmpty()
        seriesPosterUrl = catalogItem?.preferredVodPosterUrl().orEmpty()

        Log.d(TAG, "SeriesDetailFragment: seriesName='$seriesName' seriesId=$seriesId initialSeason=$initialSeason initialEpisode=$initialEpisode")
        return ComposeView(requireContext()).apply {
            setContent {
                WalacTVTheme {
                    SeriesDetailScreen(
                        seriesName = seriesName,
                        seriesId = seriesId,
                        initialSeason = initialSeason,
                        initialEpisode = initialEpisode,
                        initialSeriesItem = catalogItem,
                        repository = repository,
                        progressReloadTrigger = detailProgressReloadTrigger,
                        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() }
                    ) { item, allEpisodesForSeries, logicalEpisodes, resumePositionMs ->
                        playEpisode(item, allEpisodesForSeries, logicalEpisodes, resumePositionMs)
                    }
                }
            }
        }
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun playEpisode(
        item: CatalogItem,
        allEpisodesForSeries: List<CatalogItem>,
        logicalEpisodes: List<CatalogItem>,
        resumePositionMs: Long = 0L,
        selectedStreamUrl: String? = null,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val catalogId = item.seriesKey ?: item.seriesProviderId ?: item.providerId ?: item.stableId.substringAfter(':')
            val preference = runCatching {
                repository.getPlaybackPreference("series", catalogId)
            }.getOrNull()
            playEpisodeWithPreference(
                item,
                allEpisodesForSeries,
                logicalEpisodes,
                preference,
                catalogId,
                resumePositionMs,
                selectedStreamUrl,
            )
        }
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private suspend fun playEpisodeWithPreference(
        item: CatalogItem,
        allEpisodesForSeries: List<CatalogItem>,
        logicalEpisodes: List<CatalogItem>,
        preference: PlaybackPreferenceDto?,
        catalogId: String,
        resumePositionMs: Long = 0L,
        selectedStreamUrl: String? = null,
    ) {
        val preferredLanguage = preference?.audioLanguage
            ?: PreferencesManager.getPreferredLanguageOrDefault()
        val selectedSourceEpisode = selectedStreamUrl?.let { url ->
            allEpisodesForSeries.find {
                it.seriesName == item.seriesName &&
                    it.seasonNumber == item.seasonNumber &&
                    it.episodeNumber == item.episodeNumber &&
                    it.streamOptions.any { stream -> stream.url == url }
            }
        }
        val episodeToPlay = selectedSourceEpisode ?: allEpisodesForSeries.find {
            it.seriesName == item.seriesName &&
                it.seasonNumber == item.seasonNumber &&
                it.episodeNumber == item.episodeNumber &&
                normalizeLanguageCode(it.idioma) == normalizeLanguageCode(preferredLanguage)
        } ?: allEpisodesForSeries.find { it.stableId == item.stableId } ?: item
        val playableEpisode = repository.orderStreamsForPlayback(episodeToPlay)
        // Sin eleccion manual: primero directo del proveedor, luego el torrent
        // con mas seeds (applyGradient mantiene la descarga pegada al playhead).
        val stream = selectedStreamUrl?.let { url ->
            playableEpisode.streamOptions.firstOrNull { it.url == url }
        } ?: playableEpisode.streamOptions.firstOrNull { !it.isTorrent && it.url.isNotBlank() }
            ?: playableEpisode.streamOptions.filter { it.isTorrent }.bestTorrentFirst().firstOrNull()
            ?: playableEpisode.streamOptions.firstOrNull { it.url.isNotBlank() || it.isTorrent } ?: return
        Log.d(TAG, "TMDB_SERIES_PLAY item=${item.tmdbDebug()} episode=${episodeToPlay.tmdbDebug()}")

        val currentIndex = logicalEpisodes.indexOfFirst {
            it.seriesName == episodeToPlay.seriesName &&
                    it.seasonNumber == episodeToPlay.seasonNumber &&
                    it.episodeNumber == episodeToPlay.episodeNumber
        }
        val nextEpisodeCallback: (() -> Unit)? = if (currentIndex >= 0 && currentIndex < logicalEpisodes.size - 1) {
            { playEpisode(logicalEpisodes[currentIndex + 1], allEpisodesForSeries, logicalEpisodes) }
        } else {
            null
        }
        val previousEpisodeCallback: (() -> Unit)? = if (currentIndex > 0) {
            { playEpisode(logicalEpisodes[currentIndex - 1], allEpisodesForSeries, logicalEpisodes) }
        } else {
            null
        }

        val seriesContentId = episodeToPlay.playbackContentId()
        Log.d(TAG, "SERIES_CONTENT_ID providerId=${episodeToPlay.providerId} stableId=${episodeToPlay.stableId} -> $seriesContentId")

        val playerFragment = PlayerFragment()
        val unifiedOptions = playableEpisode.streamOptions.toUnifiedOptions()
        playerFragment.initialize(
            streamUrl = stream.url,
            overlayNumber = item.kind.name,
            overlayTitle = playableEpisode.title,
            overlayMeta = buildEpisodeLabel(playableEpisode.seasonNumber, playableEpisode.episodeNumber).ifBlank { playableEpisode.subtitle },
            overlayDescription = playableEpisode.description.ifBlank { item.description },
            overlayLogoUrl = seriesPosterUrl,
            overlayBackdropUrl = seriesBackdropUrl,
            overlayRating = playableEpisode.voteAverage ?: item.voteAverage,
            contentKind = item.kind,
            onNavigateChannel = { _ -> },
            onNavigateOption = { _ -> },
            onDirectChannelNumber = { _ -> false },
            onToggleFavorite = { false },
            onOpenFavorites = { false },
            onOpenRecents = { false },
            onNextEpisode = nextEpisodeCallback,
            onPreviousEpisode = previousEpisodeCallback,
            allSeriesEpisodes = allEpisodesForSeries,
            currentEpisode = playableEpisode,
            contentId = seriesContentId,
            positionMs = resumePositionMs,
            onPlayerClosed = {
                view?.requestFocus()
                detailProgressReloadTrigger++
            },
            onProgressSaved = ComposeMainFragment.progressSavedCallback,
            unifiedStreamOptions = unifiedOptions,
            onSelectUnifiedOption = { selectedIndex, resumeMs ->
                val selectedOption = unifiedOptions.getOrNull(selectedIndex) ?: return@initialize
                playEpisode(
                    playableEpisode,
                    allEpisodesForSeries,
                    logicalEpisodes,
                    resumeMs,
                    selectedOption.url,
                )
            },
            playbackCatalogId = catalogId,
            playbackPreference = preference,
        )
        val fragmentManager = requireActivity().supportFragmentManager
        fragmentManager.findFragmentById(R.id.player_container)?.let { existing ->
            fragmentManager.beginTransaction()
                .remove(existing)
                .commitNow()
        }

        fragmentManager.beginTransaction()
            .replace(R.id.player_container, playerFragment, PLAYER_FRAGMENT_TAG)
            .commitNow()

        val container = requireActivity().findViewById<FrameLayout>(R.id.player_container)
        container.visibility = View.VISIBLE
        container.isFocusable = true
        container.isFocusableInTouchMode = true
        container.requestFocus()
    }
}

@Composable
fun SeriesDetailScreen(
    seriesName: String,
    seriesId: String? = null,
    initialSeason: Int? = null,
    initialEpisode: Int? = null,
    initialSeriesItem: CatalogItem?,
    repository: IptvRepository,
    progressReloadTrigger: Int = 0,
    onBack: () -> Unit,
    onEpisodeClick: (CatalogItem, List<CatalogItem>, List<CatalogItem>, Long) -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var focusedEpisode by remember { mutableStateOf<CatalogItem?>(null) }
    var contextEpisode by remember { mutableStateOf<CatalogItem?>(null) }
    var sourceEpisode by remember { mutableStateOf<CatalogItem?>(null) }
    var sourceStreams by remember { mutableStateOf<List<StreamOption>>(emptyList()) }
    var sourceLoading by remember { mutableStateOf(false) }
    var sourceError by remember { mutableStateOf(false) }
    var sourceSelectedIndex by remember { mutableIntStateOf(0) }
    var localProgressReloadTrigger by remember { mutableIntStateOf(0) }
    var continueProgressLoaded by remember { mutableStateOf(false) }
    var watchedProgressLoaded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val preferredLanguage = remember { PreferencesManager.getPreferredLanguageOrDefault() }
    val loadKey = seriesId ?: seriesName
    val backFocusRequester = remember { FocusRequester() }
    val episodeFocusRequester = remember { FocusRequester() }

    val allEpisodesState = produceState<List<CatalogItem>>(initialValue = emptyList(), loadKey) {
        try {
            loadError = null
            Log.d("SeriesDetail", "load start seriesName='$seriesName' seriesId='$seriesId'")
            var episodes = if (!seriesId.isNullOrBlank()) {
                val byId = runCatching { repository.loadSeriesEpisodesById(seriesId) }.getOrElse { emptyList() }
                Log.d("SeriesDetail", "byId '$seriesId' -> ${byId.size} eps")
                if (byId.isNotEmpty()) byId else runCatching { repository.loadSeriesEpisodes(seriesName) }.getOrElse { emptyList() }.also {
                    Log.d("SeriesDetail", "fallback byName '$seriesName' -> ${it.size} eps")
                }
            } else {
                val byName = runCatching { repository.loadSeriesEpisodes(seriesName) }.getOrElse { emptyList() }
                Log.d("SeriesDetail", "byName '$seriesName' -> ${byName.size} eps")
                byName
            }
            if (episodes.isEmpty()) {
                // 1) titulo alternativo (tmdbTitle / title)
                val altName = initialSeriesItem?.tmdbTitle?.takeIf { it.isNotBlank() && it != seriesName }
                    ?: initialSeriesItem?.title?.takeIf { it.isNotBlank() && it != seriesName }
                if (altName != null) {
                    val retry = runCatching { repository.loadSeriesEpisodes(altName) }.getOrDefault(emptyList())
                    Log.d("SeriesDetail", "altName '$altName' -> ${retry.size} eps")
                    if (retry.isNotEmpty()) {
                        value = retry.sortedWith(compareBy({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }))
                        return@produceState
                    }
                }
                // 2) busqueda por nombre normalizado (sin año, lower)
                val searchCandidates = listOfNotNull(
                    seriesName.substringBefore("(").trim().takeIf { it.isNotBlank() && it != seriesName },
                    initialSeriesItem?.seriesKey?.substringBefore(" ").toString().takeIf { it.length > 3 },
                    seriesName.replace(Regex("\\s*\\(\\d{4}\\)\\s*"), "").trim().takeIf { it != seriesName },
                ).distinct()
                for (cand in searchCandidates) {
                    val retry = runCatching { repository.loadSeriesEpisodes(cand) }.getOrDefault(emptyList())
                    Log.d("SeriesDetail", "searchCand '$cand' -> ${retry.size} eps")
                    if (retry.isNotEmpty()) {
                        value = retry.sortedWith(compareBy({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }))
                        return@produceState
                    }
                }
                // 2b) correccion de typos conocidos (sutart -> stuart)
                val corrected = seriesName.replace(Regex("(?i)sutart"), "stuart")
                if (corrected != seriesName) {
                    val retry = runCatching { repository.loadSeriesEpisodes(corrected) }.getOrDefault(emptyList())
                    Log.d("SeriesDetail", "typoCorrected '$corrected' -> ${retry.size} eps")
                    if (retry.isNotEmpty()) {
                        value = retry.sortedWith(compareBy({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }))
                        return@produceState
                    }
                    // tambien probar corrected sin año y via search
                    val corr2 = corrected.substringBefore("(").trim()
                    if (corr2 != corrected) {
                        val r2 = runCatching { repository.loadSeriesEpisodes(corr2) }.getOrDefault(emptyList())
                        Log.d("SeriesDetail", "typoCorrected2 '$corr2' -> ${r2.size} eps")
                        if (r2.isNotEmpty()) {
                            value = r2.sortedWith(compareBy({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }))
                            return@produceState
                        }
                    }
                }
                // 3) fallback via API de busqueda (encuentra serie por titulo parcial)
                try {
                    // probar primero con nombre corregido para busqueda
                    val searchQuery = if (corrected != seriesName) corrected else seriesName
                    val (searchItems, _) = repository.search(searchQuery, page = 1, pageSize = 10, types = "series")
                    val match = searchItems.firstOrNull { it.kind == ContentKind.SERIES }
                    if (match != null) {
                        val sid = match.catalogId ?: match.providerId
                        if (!sid.isNullOrBlank()) {
                            val bySearchId = runCatching { repository.loadSeriesEpisodesById(sid) }.getOrDefault(emptyList())
                            Log.d("SeriesDetail", "search fallback sid '$sid' -> ${bySearchId.size} eps")
                            if (bySearchId.isNotEmpty()) {
                                value = bySearchId.sortedWith(compareBy({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }))
                                return@produceState
                            }
                        }
                        val bySearchName = match.seriesName ?: match.title
                        if (!bySearchName.isNullOrBlank() && bySearchName != seriesName) {
                            val bySN = runCatching { repository.loadSeriesEpisodes(bySearchName) }.getOrDefault(emptyList())
                            Log.d("SeriesDetail", "search fallback name '$bySearchName' -> ${bySN.size} eps")
                            if (bySN.isNotEmpty()) {
                                value = bySN.sortedWith(compareBy({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }))
                                return@produceState
                            }
                        }
                    }
                } catch (se: Exception) {
                    Log.w("SeriesDetail", "search fallback failed", se)
                }
                loadError = "No se encontraron episodios para '$seriesName'"
            }
            value = episodes.sortedWith(compareBy({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }))
        } catch (e: Exception) {
            Log.e("SeriesDetail", "load error", e)
            loadError = "Error: ${e.message}"
            value = emptyList()
        } finally {
            isLoading = false
        }
    }
    val allEpisodes = allEpisodesState.value

    val watchProgressRepo = remember { (context.applicationContext as WalacApp).appComponent.watchProgressRepository }
    val episodeSeriesIds = remember(allEpisodes, seriesId, initialSeriesItem) {
        buildSet {
            listOf(
                seriesId,
                initialSeriesItem?.catalogId,
                initialSeriesItem?.providerId,
                initialSeriesItem?.seriesKey,
                initialSeriesItem?.seriesProviderId,
                initialSeriesItem?.stableId,
            ).filterNotNullTo(this)
            allEpisodes.mapNotNullTo(this) { it.seriesKey }
            allEpisodes.mapNotNullTo(this) { it.seriesProviderId }
        }
    }
    val episodeSeriesNames = remember(allEpisodes, seriesName, initialSeriesItem) {
        buildSet {
            listOf(
                seriesName,
                initialSeriesItem?.seriesName,
                initialSeriesItem?.title,
                initialSeriesItem?.tmdbTitle,
            ).filterNotNullTo(this)
            allEpisodes.mapNotNullTo(this) { it.seriesName }
        }
    }
    val continueWatchingItems by produceState<List<WatchProgressDto>>(
        emptyList(), allEpisodes, episodeSeriesIds, episodeSeriesNames,
        progressReloadTrigger, localProgressReloadTrigger,
    ) {
        if (allEpisodes.isEmpty()) return@produceState
        value = watchProgressRepo.getContinueWatching().getOrDefault(emptyList())
        continueProgressLoaded = true
    }

    val watchedItems by produceState<List<WatchProgressDto>>(
        emptyList(), allEpisodes, episodeSeriesIds, episodeSeriesNames,
        progressReloadTrigger, localProgressReloadTrigger,
    ) {
        if (allEpisodes.isEmpty()) return@produceState
        value = watchProgressRepo.getWatchedItems().getOrDefault(emptyList())
        watchedProgressLoaded = true
    }

    val progressMap = remember(
        allEpisodes, episodeSeriesIds, episodeSeriesNames,
        continueWatchingItems, watchedItems,
    ) {
        buildSeriesEpisodeProgressMap(
            episodes = allEpisodes,
            progressItems = continueWatchingItems + watchedItems,
            seriesIds = episodeSeriesIds,
            seriesNames = episodeSeriesNames,
        )
    }

    val uniqueEpisodes = remember(allEpisodes, preferredLanguage) {
        allEpisodes.uniqueSeriesEpisodes(preferredLanguage)
    }

    val resumeEpisode = remember(uniqueEpisodes, progressMap, initialSeason, initialEpisode) {
        if (initialSeason != null && initialEpisode != null) {
            uniqueEpisodes.firstOrNull {
                it.seasonNumber == initialSeason && it.episodeNumber == initialEpisode
            }
        } else {
            val progressWithEpisodes = progressMap.mapNotNull { (key, progress) ->
                uniqueEpisodes.firstOrNull { it.seasonNumber == key.first && it.episodeNumber == key.second }
                    ?.let { episode -> episode to progress }
            }
            progressWithEpisodes
                .filter { (_, progress) ->
                    progress.isWatched != true &&
                        (progress.positionMs ?: 0L) > 0L &&
                        !progress.isCompleted
                }
                .maxByOrNull { (_, progress) -> progress.lastWatchedAt.orEmpty() }
                ?.first
                ?: progressWithEpisodes
                    .filter { (_, progress) -> progress.isWatched == true || progress.isCompleted }
                    .maxWithOrNull(
                        compareBy<Pair<CatalogItem, WatchProgressDto>> { (_, progress) -> progress.lastWatchedAt.orEmpty() }
                            .thenBy { (episode, _) -> episode.seasonNumber ?: Int.MIN_VALUE }
                            .thenBy { (episode, _) -> episode.episodeNumber ?: Int.MIN_VALUE },
                    )
                    ?.first
        }
    }

    val seasons = remember(uniqueEpisodes) {
        uniqueEpisodes.mapNotNull { it.seasonNumber }.distinct().sorted()
    }

    var selectedSeason by remember { mutableIntStateOf(initialSeason ?: seasons.firstOrNull() ?: 1) }
    val episodesListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val seasonsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val seriesContentId = remember(seriesId, initialSeriesItem, allEpisodes) {
        listOf(
            initialSeriesItem?.catalogId,
            initialSeriesItem?.seriesKey,
            initialSeriesItem?.providerId,
            initialSeriesItem?.seriesProviderId,
            seriesId,
            allEpisodes.firstOrNull()?.seriesKey,
        )
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty)?.substringAfterLast(":") }
            .firstOrNull { it.isNotBlank() }
    }

    val markEpisodes = markEpisodes@ { targets: List<CatalogItem> ->
        if (targets.isEmpty()) return@markEpisodes
        coroutineScope.launch {
            val pending = targets.filterNot { ep ->
                ep.seasonNumber?.let { s ->
                    ep.episodeNumber?.let { e -> progressMap[s to e] }
                }?.let { it.isWatched == true || it.isCompleted } == true
            }
            if (pending.isEmpty()) {
                contextEpisode = null
                return@launch
            }
            val results = pending.map { ep ->
                async {
                    val contentId = seriesContentId ?: ep.providerId ?: ep.stableId.substringAfterLast(":")
                    watchProgressRepo.markAsWatched(contentId, ep.seasonNumber, ep.episodeNumber)
                }
            }
            results.awaitAll()
            contextEpisode = null
            localProgressReloadTrigger++
        }
    }

    LaunchedEffect(seasons) {
        if (initialSeason == null) {
            selectedSeason = seasons.firstOrNull() ?: 1
        }
    }

    // imdb de la serie para Torrentio directo: el del item inicial o el primer
    // episodio que lo traiga (el backend lo incluye por episodio).
    val seriesImdb = remember(initialSeriesItem, allEpisodes) {
        sequence {
            yield(initialSeriesItem?.imdbId)
            yieldAll(allEpisodes.map { it.imdbId })
        }.firstOrNull { TorrentioClient.isImdbId(it) }
    }

    // Carga las fuentes (IPTV + Torrentio) al abrir el selector de un episodio
    LaunchedEffect(sourceEpisode) {
        val ep = sourceEpisode ?: return@LaunchedEffect
        sourceLoading = true
        sourceError = false
        sourceSelectedIndex = 0
        sourceStreams = emptyList()
        val iptv = ep.streamOptions.filter { !it.isTorrent && it.url.isNotBlank() }
        val torrents = if (ep.seasonNumber != null && ep.episodeNumber != null && seriesImdb != null) {
            repository.getTorrentioEpisodeStreams(seriesImdb, ep.seasonNumber, ep.episodeNumber)
        } else {
            emptyList()
        }
        sourceStreams = iptv + torrents
        // Preseleccion: primero directo (indice 0); solo si no hay directo,
        // el torrent con mas seeds.
        if (iptv.isEmpty()) {
            torrents.bestTorrentFirst().firstOrNull()?.let { best ->
                sourceSelectedIndex = sourceStreams.indexOf(best)
            }
        }
        sourceLoading = false
    }

    fun playSelectedSource() {
        val ep = sourceEpisode ?: return
        val streams = sourceStreams
        if (streams.isEmpty()) return
        val selected = streams[sourceSelectedIndex.coerceIn(0, streams.lastIndex)]
        // Construir el episodio con la fuente elegida en primer lugar para que
        // el player la reproduzca directamente (manteniendo las demas como
        // opciones de respaldo).
        val reordered = buildList {
            add(selected)
            addAll(streams.filter { it != selected })
        }
        onEpisodeClick(
            ep.copy(streamOptions = reordered),
            allEpisodes,
            uniqueEpisodes,
            0L,
        )
        sourceEpisode = null
    }

    // Reproduccion directa o, en series solo-torrentio sin urls IPTV, apertura
    // del selector de fuentes (que consulta Torrentio) igual que desktop.
    fun playOrPickSource(ep: CatalogItem, positionMs: Long) {
        if (ep.streamOptions.any { it.url.isNotBlank() || it.isTorrent }) {
            onEpisodeClick(ep, allEpisodes, uniqueEpisodes, positionMs)
        } else {
            sourceEpisode = ep
        }
    }

    LaunchedEffect(allEpisodes, progressMap, continueProgressLoaded, watchedProgressLoaded, initialSeason, initialEpisode) {
        val episodes = allEpisodes.uniqueSeriesEpisodes(preferredLanguage)
        if (episodes.isEmpty()) return@LaunchedEffect
        if (!continueProgressLoaded && initialSeason == null && initialEpisode == null) return@LaunchedEffect
        val targetEpisode = resumeEpisode
        if (targetEpisode == null) {
            delay(50.milliseconds)
            backFocusRequester.requestFocus()
            return@LaunchedEffect
        }
        val targetIndex = episodes.indexOf(targetEpisode)
        if (targetIndex >= 0) {
            selectedSeason = targetEpisode.seasonNumber ?: seasons.firstOrNull() ?: 1
            val seasonIndex = seasons.indexOf(selectedSeason)
            delay(50.milliseconds)
            if (seasonIndex >= 0) seasonsListState.scrollToItem(seasonIndex)
            episodesListState.scrollToItem(targetIndex)
            delay(50.milliseconds)
            runCatching { episodeFocusRequester.requestFocus() }
            focusedEpisode = targetEpisode
        } else {
            backFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(selectedSeason) {
        val seasonIndex = seasons.indexOf(selectedSeason)
        if (seasonIndex >= 0) {
            seasonsListState.scrollToItem(seasonIndex)
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Cargando...", color = Color.White)
        }
        return
    }

    if (loadError != null || allEpisodes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(loadError ?: "No se encontraron episodios", color = Color.White)
        }
        return
    }

    val seriesItem = initialSeriesItem ?: allEpisodes.firstOrNull { it.totalSeasons != null } ?: allEpisodes.firstOrNull()
    val bgUrl = seriesItem?.backdropUrl?.takeIf { it.isNotBlank() }
        ?: seriesItem?.tmdbPosterUrl?.takeIf { it.isNotBlank() }
        ?: seriesItem?.imageUrl?.takeIf { it.isNotBlank() }
        ?: ""
    val seriesDisplayName = seriesItem?.tmdbTitle?.takeIf { it.isNotBlank() } ?: seriesItem?.title?.takeIf { it.isNotBlank() } ?: seriesName
    val totalSeasons = seriesItem?.totalSeasons ?: seasons.size
    val year = seriesItem?.year?.toString() ?: seriesItem?.releaseDate?.take(4) ?: ""
    val genres = seriesItem?.genres?.joinToString(", ") ?: ""
    val synopsis = focusedEpisode?.description?.takeIf { it.isNotBlank() }
        ?: seriesItem?.description?.takeIf { it.isNotBlank() }
        ?: "Sin sinopsis disponible."

    Log.d("SeriesDetailScreen", "=== SERIES DETAIL DEBUG ===")
    Log.d("SeriesDetailScreen", "seriesName=$seriesName seriesDisplayName=$seriesDisplayName")
    Log.d("SeriesDetailScreen", "bgUrl='$bgUrl'")
    Log.d("SeriesDetailScreen", "backdropUrl='${seriesItem?.backdropUrl}'")
    Log.d("SeriesDetailScreen", "tmdbPosterUrl='${seriesItem?.tmdbPosterUrl}'")
    Log.d("SeriesDetailScreen", "imageUrl='${seriesItem?.imageUrl}'")
    Log.d("SeriesDetailScreen", "description='${seriesItem?.description?.take(120)}'")
    Log.d("SeriesDetailScreen", "synopsis='${synopsis.take(120)}'")
    Log.d("SeriesDetailScreen", "year=$year genres=$genres totalSeasons=$totalSeasons")

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (bgUrl.isNotBlank()) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { iv ->
                    Log.d("SeriesDetailScreen", "Glide loading bgUrl='$bgUrl' into iv=${iv.width}x${iv.height}")
                    Glide.with(iv)
                        .load(bgUrl)
                        .override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)
                        .into(iv)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Log.w("SeriesDetailScreen", "bgUrl is blank, not loading background image")
        }

        Box(modifier = Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.horizontalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.6f), Color.Transparent),
                startX = 0f, endX = 1500f
            )
        ))
        Box(modifier = Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f), Color.Black),
                startY = 400f, endY = 1080f
            )
        ))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(32.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .focusRequester(backFocusRequester)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .tvClickable { onBack() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Volver", color = Color.White, fontSize = 16.sp)
                }
            }

            item {
                Spacer(Modifier.height(50.dp))
            }

            item {
                Column(modifier = Modifier.fillMaxWidth(0.55f)) {
                    Text(seriesDisplayName, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp)
                    Spacer(Modifier.height(8.dp))
                    val metaList = listOfNotNull(
                        year.takeIf { it.isNotBlank() },
                        "$totalSeasons temporadas",
                        genres.takeIf { it.isNotBlank() },
                        seriesItem?.voteAverage?.takeIf { it > 0.0 }?.let { "★ %.1f".format(it) }
                    )
                    Text(metaList.joinToString(" • "), color = Color.LightGray, fontSize = 16.sp)
                    focusedEpisode?.let { ep ->
                        val epMeta = buildList {
                            ep.seasonNumber?.let { s -> add("T$s") }
                            ep.episodeNumber?.let { e -> add("E$e") }
                            ep.airDate?.takeIf { it.isNotBlank() }?.let { add(formatSpanishDate(it) ?: it) }
                            ep.voteAverage?.let { if (it > 0.0) add("★ %.1f".format(it)) }
                        }
                        if (epMeta.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(epMeta.joinToString(" · "), color = Color(0xFFB0BEC5), fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = synopsis, 
                        color = Color.White, 
                        fontSize = 14.sp, 
                        maxLines = 5, 
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.heightIn(min = 100.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        var playFocused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .onFocusChanged { playFocused = it.isFocused }
                         .tvClickable {
                             val episode = uniqueEpisodes.firstOrNull { it.seasonNumber == selectedSeason } ?: allEpisodes.first()
                             val positionMs = episode.seasonNumber?.let { season ->
                                 episode.episodeNumber?.let { number -> progressMap[season to number]?.positionMs }
                             } ?: 0L
                             playOrPickSource(episode, positionMs)
                         }
                                .background(if (playFocused) Color.LightGray else Color.White, androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(Modifier.width(8.dp))
                            Text("Reproducir", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(32.dp))
            }

            if (seasons.isNotEmpty()) {
                item {
                    androidx.compose.foundation.lazy.LazyRow(
                        state = seasonsListState,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(seasons) { season ->
                            val isSelected = season == selectedSeason
                            var chipFocused by remember { mutableStateOf(false) }
                            Text(
                                text = "Temporada $season",
                                color = when {
                                    isSelected -> Color.Black
                                    chipFocused -> Color.White
                                    else -> Color.LightGray
                                },
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .onFocusChanged { chipFocused = it.isFocused }
                                    .tvClickable {
                                        selectedSeason = season
                                        val idx = uniqueEpisodes.indexOfFirst { it.seasonNumber == season }
                                        if (idx >= 0) {
                                            coroutineScope.launch { episodesListState.scrollToItem(idx) }
                                        }
                                    }
                                    .background(
                                        if (isSelected) Color.White else if (chipFocused) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            item {
                androidx.compose.foundation.lazy.LazyRow(
                    state = episodesListState,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uniqueEpisodes, key = { it.stableId }) { ep ->
                        val wp = ep.seasonNumber?.let { s ->
                            ep.episodeNumber?.let { e -> progressMap[s to e] }
                        }
                        val isInitial = ep.stableId == resumeEpisode?.stableId
                        EpisodeCard(
                            item = ep,
                            watchProgress = wp,
                            onClick = { playOrPickSource(ep, wp?.positionMs ?: 0L) },
                            onFocus = {
                                focusedEpisode = ep
                                val epSeason = ep.seasonNumber ?: 1
                                if (epSeason != selectedSeason) {
                                    selectedSeason = epSeason
                                }
                            },
                            onMenuRequest = { contextEpisode = it },
                            onChooseSource = { sourceEpisode = it },
                            modifier = if (isInitial) Modifier.focusRequester(episodeFocusRequester) else Modifier,
                        )
                    }
                }
            }
        }
    }

    contextEpisode?.let { ep ->
        EpisodeOptionsMenu(
            episode = ep,
            onMarkEpisode = { markEpisodes(listOf(ep)) },
            onMarkSeason = { markEpisodes(uniqueEpisodes.filter { it.seasonNumber == ep.seasonNumber }) },
            onMarkPrevious = {
                val targetIndex = uniqueEpisodes.indexOf(ep)
                markEpisodes(
                    uniqueEpisodes.filter { candidate ->
                        when {
                            candidate.stableId == ep.stableId -> true
                            ep.seasonNumber != null -> {
                                val candidateSeason = candidate.seasonNumber
                                candidateSeason != null &&
                                    (candidateSeason < ep.seasonNumber ||
                                        (candidateSeason == ep.seasonNumber &&
                                            (candidate.episodeNumber ?: 0) <= (ep.episodeNumber ?: 0)))
                            }
                            targetIndex >= 0 -> uniqueEpisodes.indexOf(candidate) < targetIndex
                            else -> false
                        }
                    },
                )
            },
            onDismiss = { contextEpisode = null },
        )
    }

    sourceEpisode?.let { ep ->
        SourcePickerDialog(
            episode = ep,
            streams = sourceStreams,
            loading = sourceLoading,
            error = sourceError,
            selectedIndex = sourceSelectedIndex,
            onSelect = { sourceSelectedIndex = it },
            onPlay = { playSelectedSource() },
            onDismiss = { sourceEpisode = null },
        )
    }
}

@Composable
private fun SourcePickerDialog(
    episode: CatalogItem,
    streams: List<StreamOption>,
    loading: Boolean,
    error: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(Unit) {
        delay(50.milliseconds)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }
    LaunchedEffect(streams, selectedIndex) {
        focusedIndex = selectedIndex
    }

    val torrents = streams.filter { it.isTorrent }
    val iptv = streams.filter { !it.isTorrent }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> { focusedIndex = (focusedIndex - 1).coerceAtLeast(0); true }
                        Key.DirectionDown -> {
                            focusedIndex = (focusedIndex + 1).coerceAtMost((streams.size - 1).coerceAtLeast(0))
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            if (streams.isNotEmpty()) { onSelect(focusedIndex); onPlay() }
                            true
                        }
                        Key.Back, Key.Escape -> { onDismiss(); true }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(560.dp)
                    .background(Color(0xFF1A1A2E), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF2E2E4E), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Fuentes · ${buildEpisodeLabel(episode.seasonNumber, episode.episodeNumber)}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    episode.title,
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))

                when {
                    loading -> Text("Buscando fuentes en Torrentio...", color = Color.LightGray, fontSize = 14.sp)
                    streams.isEmpty() -> Text(
                        if (error) "No se pudieron cargar las fuentes" else "Sin fuentes disponibles",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                    )
                    else -> {
                        if (iptv.isNotEmpty()) {
                            Text("DIRECTO IPTV", color = Color(0xFF6FA8DC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            iptv.forEachIndexed { idx, stream ->
                                val globalIdx = idx
                                val isSelected = globalIdx == focusedIndex
                                SourceRow(
                                    label = stream.label,
                                    quality = stream.quality,
                                    language = stream.language,
                                    seeders = null,
                                    size = null,
                                    isTorrent = false,
                                    isSelected = isSelected,
                                    onClick = { focusedIndex = globalIdx; onSelect(globalIdx) },
                                )
                            }
                        }
                        if (torrents.isNotEmpty()) {
                            Text("TORRENT · TORRENTIO", color = Color(0xFFD68FE2), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            torrents.forEachIndexed { idx, stream ->
                                val globalIdx = iptv.size + idx
                                val isSelected = globalIdx == focusedIndex
                                SourceRow(
                                    label = stream.torrentTitle ?: stream.label,
                                    quality = stream.quality,
                                    language = stream.language,
                                    seeders = stream.seeders,
                                    size = stream.sizeBytes,
                                    isTorrent = true,
                                    isSelected = isSelected,
                                    onClick = { focusedIndex = globalIdx; onSelect(globalIdx) },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (streams.isNotEmpty()) Color.White else Color.Gray)
                        .then(if (streams.isNotEmpty()) Modifier.tvClickable {
                            onSelect(focusedIndex); onPlay()
                        } else Modifier)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Reproducir",
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    label: String,
    quality: String?,
    language: String?,
    seeders: Int?,
    size: Long?,
    isTorrent: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .tvClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (isTorrent) "⬤" else "▶",
                color = if (isTorrent) Color(0xFFD68FE2) else Color(0xFF6FA8DC),
                fontSize = 12.sp,
            )
            Text(
                text = label,
                color = if (isSelected) Color.White else Color.LightGray,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            quality?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it.uppercase(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            seeders?.let {
                Text("$it seeds", color = Color(0xFF46D369), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            size?.let {
                val gb = it / (1024.0 * 1024.0 * 1024.0)
                Text(
                    if (gb >= 1) String.format(java.util.Locale.US, "%.1f GB", gb) else "${it / (1024 * 1024)} MB",
                    color = Color.Gray,
                    fontSize = 12.sp,
                )
            }
            language?.takeIf { it.isNotBlank() && !isTorrent }?.let {
                Text(
                    it,
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun EpisodeOptionsMenu(
    episode: CatalogItem,
    onMarkEpisode: () -> Unit,
    onMarkSeason: () -> Unit,
    onMarkPrevious: () -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        stringResource(R.string.episode_menu_mark_this),
        stringResource(R.string.episode_menu_mark_season),
        stringResource(R.string.episode_menu_mark_previous),
    )

    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(50.milliseconds)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> {
                            selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                            true
                        }
                        Key.DirectionDown -> {
                            selectedIndex = (selectedIndex + 1).coerceAtMost(options.lastIndex)
                            true
                        }
                        Key.DirectionCenter,
                        Key.Enter -> {
                            when (selectedIndex) {
                                0 -> onMarkEpisode()
                                1 -> onMarkSeason()
                                2 -> onMarkPrevious()
                            }
                            onDismiss()
                            true
                        }
                        Key.Back,
                        Key.Escape -> { onDismiss(); true }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(420.dp)
                    .background(Color(0xFF1A1A2E), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF2E2E4E), RoundedCornerShape(16.dp))
                    .padding(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.vod_menu_title),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        buildEpisodeLabel(episode.seasonNumber, episode.episodeNumber).ifBlank { episode.title },
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    options.forEachIndexed { index, label ->
                        val isSelected = index == selectedIndex
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .tvClickable {
                                    when (index) {
                                        0 -> onMarkEpisode()
                                        1 -> onMarkSeason()
                                        2 -> onMarkPrevious()
                                    }
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                label,
                                color = if (isSelected) Color.White else Color.LightGray,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeCard(
    item: CatalogItem,
    watchProgress: WatchProgressDto? = null,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    onMenuRequest: ((CatalogItem) -> Unit)? = null,
    onChooseSource: ((CatalogItem) -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    var keyDownMillis by remember { mutableLongStateOf(0L) }
    var consumeClick by remember { mutableStateOf(false) }

    val isWatched = item.isWatched || watchProgress?.isWatched == true
    val progressPercent = watchProgress?.progressPercent ?: 0
    val hasProgress = progressPercent in 1..99 && !isWatched

    val clickModifier = if (onMenuRequest != null) {
        Modifier
            .clickable { if (!consumeClick) onClick() }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter) &&
                    !consumeClick
                ) {
                    onClick()
                    true
                } else false
            }
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            keyDownMillis = event.nativeKeyEvent.downTime
                            consumeClick = false
                            false
                        }
                        KeyEventType.KeyUp -> {
                            val elapsed = event.nativeKeyEvent.eventTime - keyDownMillis
                            if (elapsed >= LONG_PRESS_THRESHOLD_MS) {
                                consumeClick = true
                                onMenuRequest(item)
                                true
                            } else {
                                consumeClick = false
                                false
                            }
                        }
                        else -> false
                    }
                } else false
            }
    } else {
        Modifier.tvClickable { onClick() }
    }

    Column(
        modifier = modifier
            .width(240.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .onFocusChanged { 
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .then(clickModifier)
            .border(
                width = 2.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .scale(if (isFocused) 1.03f else 1f)
            .background(Color.DarkGray.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.DarkGray)
        ) {
            val imageUrl = item.stillPath?.takeIf { it.isNotBlank() }
                ?: item.backdropUrl?.takeIf { it.isNotBlank() }
                ?: item.imageUrl.takeIf { it.isNotBlank() }
                ?: ""
            Log.d("EpisodeCard", "ep=${item.episodeNumber} imageUrl='$imageUrl' stillPath='${item.stillPath}' backdropUrl='${item.backdropUrl}'")
            if (imageUrl.isNotBlank()) {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    },
                    update = { iv ->
                        Glide.with(iv)
                            .load(imageUrl)
                            .into(iv)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            if (isWatched) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
                WatchedBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.7f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = item.episodeNumber?.toString() ?: "?", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            val runtime = item.runtimeMinutes
            val rating = item.voteAverage
            if ((runtime != null && runtime > 0) || (rating != null && rating > 0.0)) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.7f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rating != null && rating > 0.0) {
                        Text("★", color = Color(0xFFFFC107), fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("%.1f".format(rating), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if ((runtime != null && runtime > 0) && (rating != null && rating > 0f)) {
                        Spacer(Modifier.width(8.dp))
                    }
                    if (runtime != null && runtime > 0) {
                        Text("$runtime min", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isFocused) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
        }
        
        if (hasProgress) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressPercent / 100f)
                        .background(IptvAccent)
                )
            }
        }

        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = item.title,
                color = if (isFocused) Color.White else Color.LightGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onChooseSource != null) {
                var sourcesFocused by remember { mutableStateOf(false) }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .background(
                            if (sourcesFocused) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
                            androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                        )
                        .onFocusChanged { sourcesFocused = it.isFocused }
                        .tvClickable { onChooseSource(item) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Fuentes",
                        color = if (sourcesFocused) Color.White else Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

fun formatSpanishDate(dateString: String?): String? {
    if (dateString.isNullOrBlank()) return null
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val formatter = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.Builder().setLanguage("es").setRegion("ES").build())
        val date = parser.parse(dateString)
        date?.let { formatter.format(it) } ?: dateString
    } catch (e: Exception) {
        dateString
    }
}
