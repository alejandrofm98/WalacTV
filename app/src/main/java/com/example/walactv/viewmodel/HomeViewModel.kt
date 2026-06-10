package com.example.walactv.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walactv.BrowseSection
import com.example.walactv.CatalogFilters
import com.example.walactv.CatalogItem
import com.example.walactv.CatalogMemory
import com.example.walactv.ContentKind
import com.example.walactv.HomeCatalog
import com.example.walactv.IptvRepository
import com.example.walactv.WatchProgressItem
import com.example.walactv.WatchProgressRepository
import com.example.walactv.local.ContentCacheManager
import com.example.walactv.ChannelStateStore
import com.example.walactv.tmdbDebug
import com.example.walactv.ui.compose.buildEpisodeLabel
import com.example.walactv.ui.compose.buildTmdbImageUrl
import com.example.walactv.ui.compose.cleanDisplayText
import com.example.walactv.ui.compose.matchesByProviderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val watchProgressRepo: WatchProgressRepository,
    private val contentCacheManager: ContentCacheManager,
    private val channelStateStore: ChannelStateStore,
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    // ── Catalog state ──────────────────────────────────────────────────────

    private val _homeCatalog = MutableStateFlow<HomeCatalog?>(null)
    val homeCatalog: StateFlow<HomeCatalog?> = _homeCatalog.asStateFlow()

    private val _homeSections = MutableStateFlow<List<BrowseSection>>(emptyList())
    val homeSections: StateFlow<List<BrowseSection>> = _homeSections.asStateFlow()

    private val _continueWatchingSection = MutableStateFlow<BrowseSection?>(null)
    val continueWatchingSection: StateFlow<BrowseSection?> = _continueWatchingSection.asStateFlow()

    private val _continueWatchingEntries = MutableStateFlow<Map<String, WatchProgressItem>>(emptyMap())
    val continueWatchingEntries: StateFlow<Map<String, WatchProgressItem>> = _continueWatchingEntries.asStateFlow()

    private val _searchableItems = MutableStateFlow<List<CatalogItem>>(emptyList())
    val searchableItems: StateFlow<List<CatalogItem>> = _searchableItems.asStateFlow()

    private val _channelLineup = MutableStateFlow<List<CatalogItem>>(emptyList())
    val channelLineup: StateFlow<List<CatalogItem>> = _channelLineup.asStateFlow()

    private val _selectedHero = MutableStateFlow<CatalogItem?>(null)
    val selectedHero: StateFlow<CatalogItem?> = _selectedHero.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── Filter state ───────────────────────────────────────────────────────

    internal val _channelFilters = MutableStateFlow(CatalogFilters())
    val channelFilters: StateFlow<CatalogFilters> = _channelFilters.asStateFlow()

    internal val _movieFilters = MutableStateFlow(CatalogFilters())
    val movieFilters: StateFlow<CatalogFilters> = _movieFilters.asStateFlow()

    internal val _seriesFilters = MutableStateFlow(CatalogFilters())
    val seriesFilters: StateFlow<CatalogFilters> = _seriesFilters.asStateFlow()

    internal val _channelFilterCountry = MutableStateFlow<String?>(null)
    val channelFilterCountry: StateFlow<String?> = _channelFilterCountry.asStateFlow()

    internal val _movieFilterCountry = MutableStateFlow<String?>(null)
    val movieFilterCountry: StateFlow<String?> = _movieFilterCountry.asStateFlow()

    internal val _seriesFilterCountry = MutableStateFlow<String?>(null)
    val seriesFilterCountry: StateFlow<String?> = _seriesFilterCountry.asStateFlow()

    // ── Sync state ─────────────────────────────────────────────────────────

    enum class ContentSyncState { IDLE, CHECKING, SYNCING, READY, ERROR }

    private val _contentSyncState = MutableStateFlow(ContentSyncState.IDLE)
    val contentSyncState: StateFlow<ContentSyncState> = _contentSyncState.asStateFlow()

    private val _contentSyncError = MutableStateFlow<String?>(null)
    val contentSyncError: StateFlow<String?> = _contentSyncError.asStateFlow()

    private val _currentSyncLabel = MutableStateFlow("")
    val currentSyncLabel: StateFlow<String> = _currentSyncLabel.asStateFlow()

    private val _currentSyncCount = MutableStateFlow(0)
    val currentSyncCount: StateFlow<Int> = _currentSyncCount.asStateFlow()

    private val _overallSyncProgress = MutableStateFlow(0f)
    val overallSyncProgress: StateFlow<Float> = _overallSyncProgress.asStateFlow()

    // ── Internal versioning for stale-request guard ────────────────────────

    private var continueWatchingRequestVersion: Int = 0

    // ── Loading ────────────────────────────────────────────────────────────

    fun startLoad(forceRefresh: Boolean = false) {
        if (_homeCatalog.value != null && !forceRefresh) {
            refreshEvents()
            return
        }

        viewModelScope.launch {
            _errorMessage.value = null
            _contentSyncState.value = ContentSyncState.CHECKING
            Log.d(TAG, "startLoad: beginning content sync (forceRefresh=$forceRefresh)")

            val token = runCatching { repository.getAccessToken() }.getOrNull() ?: ""

            val needsChannels = contentCacheManager.needsSyncChannels(token)

            if (!needsChannels) {
                _contentSyncState.value = ContentSyncState.READY
                loadChannelFilters()
            } else {
                _contentSyncState.value = ContentSyncState.SYNCING
                _currentSyncLabel.value = ""
                _currentSyncCount.value = 0
                _overallSyncProgress.value = 0f

                val results = mutableListOf<Result<*>>()
                val totalSteps = 1

                _currentSyncLabel.value = "Sincronizando canales"
                val r = contentCacheManager.syncChannels(token)
                results.add(r)
                _overallSyncProgress.value = 1f / totalSteps
                _currentSyncCount.value = r.getOrNull() as? Int ?: 0

                if (results.any { it.isFailure }) {
                    _contentSyncState.value = ContentSyncState.ERROR
                    _contentSyncError.value = "Error al sincronizar contenido"
                } else {
                    _currentSyncLabel.value = "Sincronización completada"
                    _currentSyncCount.value = 0
                    _overallSyncProgress.value = 1f
                    _contentSyncState.value = ContentSyncState.READY
                    loadChannelFilters()
                }
            }

            runCatching { repository.loadHomeCatalog(forceRefresh = forceRefresh) }
                .onSuccess { catalog ->
                    _homeCatalog.value = catalog
                    updateStateFromCatalog(catalog)
                    _isLoaded.value = true
                }
                .onFailure {
                    if (!_isLoaded.value) _errorMessage.value = it.message ?: "Error al cargar la aplicacion"
                }
        }
    }

    fun refreshEvents() {
        viewModelScope.launch {
            runCatching { repository.loadEventsOnly() }
                .onSuccess { catalog ->
                    val eventSections = catalog.sections
                    repository.updateHomeEventsCache(eventSections)
                    _homeCatalog.value = _homeCatalog.value?.let { current ->
                        val nonEventSections = current.sections.filterNot { section ->
                            section.items.any { it.kind == ContentKind.EVENT }
                        }
                        current.copy(
                            sections = eventSections + nonEventSections,
                            searchableItems = (eventSections.flatMap(BrowseSection::items) + current.searchableItems.filterNot { it.kind == ContentKind.EVENT })
                                .distinctBy(CatalogItem::stableId),
                        )
                    } ?: catalog
                    updateStateFromCatalog(_homeCatalog.value ?: catalog)
                }
                .onFailure { Log.w(TAG, "No se pudieron refrescar eventos", it) }
        }
    }

    // ── State derivation ───────────────────────────────────────────────────

    fun updateStateFromCatalog(catalog: HomeCatalog) {
        catalog.favoriteItems?.let { favorites ->
            channelStateStore.replaceFavoriteIds(favorites.map(CatalogItem::stableId))
        }
        _searchableItems.value = catalog.searchableItems
        CatalogMemory.searchableItems = catalog.searchableItems
        _channelLineup.value = catalog.searchableItems.filter { it.kind == ContentKind.CHANNEL }
        rebuildHomeSections()

        if (_selectedHero.value == null || catalog.searchableItems.none { it.stableId == _selectedHero.value?.stableId }) {
            _selectedHero.value = defaultItemForMode()
        }
        loadContinueWatching()
    }

    fun rebuildHomeSections() {
        val baseSections = _homeCatalog.value?.sections.orEmpty()
        _homeSections.value = _continueWatchingSection.value?.let { cw ->
            if (baseSections.isEmpty()) listOf(cw)
            else listOf(baseSections.first()) + cw + baseSections.drop(1)
        } ?: baseSections
    }

    // ── Continue watching ──────────────────────────────────────────────────

    fun loadContinueWatching() {
        val requestVersion = ++continueWatchingRequestVersion
        val searchableSnapshot = _searchableItems.value
        viewModelScope.launch {
            try {
                val inProgressItems = watchProgressRepo.getContinueWatching()
                val watchedItems = watchProgressRepo.getWatchedItems()

                val entryMap = mutableMapOf<String, WatchProgressItem>()

                (inProgressItems + watchedItems).forEach { wp ->
                    val prefix = if (wp.contentType == "series") "series" else "movie"
                    entryMap[wp.contentId] = wp
                    entryMap["$prefix:${wp.contentId}"] = wp
                    val bareId = wp.contentId.substringAfterLast(":")
                    entryMap["$prefix:$bareId"] = wp
                    val normalizedKey = when (wp.contentType) {
                        "series" -> wp.seriesName?.trim()?.lowercase()
                        else -> wp.normalizedTitle.trim().lowercase()
                            .ifBlank { wp.title.trim().lowercase() }
                    }
                    if (!normalizedKey.isNullOrBlank()) {
                        entryMap["title:$normalizedKey"] = wp
                    }
                }

                if (requestVersion != continueWatchingRequestVersion) return@launch
                _continueWatchingEntries.value = entryMap

                val dedupedItems = inProgressItems
                    .groupBy { wp ->
                        if (wp.contentType == "series" && wp.seriesName != null)
                            "series:${wp.seriesName}"
                        else
                            "movie:${wp.contentId}"
                    }
                    .map { (_, entries) -> entries.maxByOrNull { it.lastWatchedAt }!! }
                    .sortedByDescending { it.lastWatchedAt }

                if (dedupedItems.isNotEmpty()) {
                    val catalogItems = dedupedItems.map { wp ->
                        buildContinueWatchingItem(wp, searchableSnapshot).also { synthetic ->
                            entryMap[synthetic.stableId] = wp
                        }
                    }
                    _continueWatchingSection.value = BrowseSection("Continuar viendo", catalogItems)
                } else {
                    _continueWatchingSection.value = null
                }

                rebuildHomeSections()

            } catch (e: Exception) {
                Log.w(TAG, "Could not load continue watching[$requestVersion]: ${e.message}", e)
            }
        }
    }

    // ── Continue watching item builder ─────────────────────────────────────

    private fun buildContinueWatchingItem(
        wp: WatchProgressItem,
        searchableSnapshot: List<CatalogItem>,
    ): CatalogItem {
        val kind = if (wp.contentType == "series") ContentKind.SERIES else ContentKind.MOVIE
        val subtitle = if (wp.contentType == "series") buildEpisodeLabel(wp.seasonNumber, wp.episodeNumber) else ""
        val fallbackTitle = wp.normalizedTitle.cleanDisplayText()
            .ifBlank { wp.seriesName.cleanDisplayText() }
            .ifBlank { wp.title.cleanDisplayText() }
        val fallbackStableId = if (wp.contentType == "series") "cw_series:${wp.contentId}" else "cw_movie:${wp.contentId}"

        val homeSnapshot = _homeSections.value.asSequence().flatMap { it.items.asSequence() }.toList()
        val richSnapshot = homeSnapshot + searchableSnapshot

        val matched = when (wp.contentType) {
            "movie" -> richSnapshot.firstOrNull { it.kind == ContentKind.MOVIE && it.matchesByProviderId(wp.contentId) }
            "series" -> findSeriesMatch(wp, richSnapshot)
                ?: richSnapshot.firstOrNull { it.kind == ContentKind.SERIES && it.matchesByProviderId(wp.contentId) }
            else -> null
        }

        Log.d(
            TAG,
            "TMDB_CW build contentId=${wp.contentId} type=${wp.contentType} series=${wp.seriesName} matched=${matched.tmdbDebug()} " +
                "wpTmdb=${wp.tmdbTitle.orEmpty()} wpBackdrop=${wp.backdropPath.orEmpty()} wpPoster=${wp.posterPath.orEmpty()} wpImage=${wp.imageUrl.take(80)}",
        )

        return matched?.copy(
            stableId = fallbackStableId,
            providerId = wp.contentId,
            title = fallbackTitle,
            normalizedTitle = null,
            subtitle = subtitle,
            description = matched.description.cleanDisplayText().ifBlank { wp.title },
            imageUrl = matched.imageUrl.ifBlank { wp.imageUrl },
            seriesName = matched.seriesName.cleanDisplayText().ifBlank { wp.seriesName.orEmpty() }.ifBlank { null },
        ) ?: wp.toCatalogItemFallback(
            stableId = fallbackStableId,
            subtitle = subtitle,
            imageUrl = wp.imageUrl,
            kind = kind,
            title = fallbackTitle,
        )
    }

    private fun WatchProgressItem.toCatalogItemFallback(
        stableId: String,
        subtitle: String,
        imageUrl: String,
        kind: ContentKind,
        title: String,
    ): CatalogItem {
        val tmdbPosterUrl = buildTmdbImageUrl(posterPath, "w500")
        val backdropUrl = buildTmdbImageUrl(backdropPath, "w1280")
        return CatalogItem(
            stableId = stableId,
            providerId = contentId,
            title = title,
            normalizedTitle = null,
            subtitle = subtitle,
            description = overview.cleanDisplayText().ifBlank { this.title },
            imageUrl = imageUrl.ifBlank { tmdbPosterUrl.orEmpty() },
            kind = kind,
            group = "Continuar viendo",
            badgeText = if (kind == ContentKind.MOVIE) "Pelicula" else "Serie",
            seriesName = seriesName,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            streamOptions = emptyList(),
            overviewEn = overviewEn,
            voteAverage = voteAverage,
            voteCount = voteCount,
            runtimeMinutes = runtimeMinutes,
            genres = genres,
            backdropUrl = backdropUrl,
            tmdbPosterUrl = tmdbPosterUrl,
            tagline = tagline,
            releaseDate = releaseDate,
            year = year,
            tmdbTitle = tmdbTitle,
            totalSeasons = totalSeasons,
        )
    }

    private fun findSeriesMatch(
        wp: WatchProgressItem,
        items: List<CatalogItem>,
    ): CatalogItem? {
        val seriesName = wp.seriesName ?: return null
        val seriesItems = items.filter { it.kind == ContentKind.SERIES }
        seriesItems.firstOrNull { it.seriesName == seriesName }?.let { return it }
        seriesItems.firstOrNull { it.seriesName?.equals(seriesName, ignoreCase = true) == true }?.let { return it }
        val baseName = seriesName.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ").trim()
        seriesItems.firstOrNull {
            it.seriesName?.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ")?.trim().equals(baseName, ignoreCase = true)
        }?.let { return it }
        seriesItems.firstOrNull { item ->
            val ns = item.seriesName ?: return@firstOrNull false
            ns.contains(seriesName, ignoreCase = true) || seriesName.contains(ns, ignoreCase = true)
        }?.let { return it }
        seriesItems.firstOrNull {
            it.title.contains(seriesName, ignoreCase = true) || seriesName.contains(it.title, ignoreCase = true)
        }?.let { return it }
        return null
    }

    // ── Filters ────────────────────────────────────────────────────────────

    fun loadChannelFilters() {
        viewModelScope.launch {
            runCatching { contentCacheManager.getLocalChannelFilters() }
                .onSuccess { _channelFilters.value = it }
                .onFailure { Log.e(TAG, "Error loading local channel filters", it) }
        }
    }

    fun ensureFiltersLoaded(kind: ContentKind, country: String? = null) {
        viewModelScope.launch {
            runCatching { repository.loadCatalogFilters(kind, country) }
                .onSuccess { filters ->
                    when (kind) {
                        ContentKind.CHANNEL -> { _channelFilters.value = filters; _channelFilterCountry.value = country }
                        ContentKind.MOVIE -> { _movieFilters.value = filters; _movieFilterCountry.value = country }
                        ContentKind.SERIES -> { _seriesFilters.value = filters; _seriesFilterCountry.value = country }
                        ContentKind.EVENT -> Unit
                    }
                }
                .onFailure { Log.e(TAG, "No se pudieron cargar filtros para $kind", it) }
        }
    }

    // ── Auth ────────────────────────────────────────────────────────────────

    suspend fun signIn(username: String, password: String): Result<Unit> {
        return runCatching { repository.signIn(username, password) }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    fun defaultItemForMode(mode: String? = null): CatalogItem? {
        val resolvedMode = mode ?: "Home"
        return when (resolvedMode) {
            "Home" -> _homeSections.value.firstNotNullOfOrNull { it.items.firstOrNull() }
            "TV" -> _searchableItems.value.firstOrNull { it.kind == ContentKind.CHANNEL }
            "Events" -> _searchableItems.value.firstOrNull { it.kind == ContentKind.EVENT }
            "Discover" -> _searchableItems.value.firstOrNull { it.kind == ContentKind.MOVIE || it.kind == ContentKind.SERIES }
            else -> null
        }
    }

    fun removeContinueWatchingLocally(
        contentId: String? = null,
        seriesName: String? = null,
    ) {
        _continueWatchingEntries.value = _continueWatchingEntries.value
            .filterValues { wp ->
                if (seriesName != null) wp.seriesName != seriesName
                else wp.contentId != contentId
            }
        _continueWatchingSection.value = _continueWatchingSection.value?.let { section ->
            val filtered = section.items.filter { item ->
                if (seriesName != null) item.seriesName != seriesName
                else item.providerId != contentId
            }
            if (filtered.isEmpty()) null else section.copy(items = filtered)
        }
        rebuildHomeSections()
    }

    fun upsertContinueWatchingEntry(item: WatchProgressItem) {
        val searchableSnapshot = _searchableItems.value
        val previous = _continueWatchingEntries.value[item.contentId]
            ?: _continueWatchingEntries.value["${item.contentType}:${item.contentId}"]
            ?: _continueWatchingEntries.value[item.contentId.substringAfterLast(":")]
        val progressItem = if (item.contentType == "series" && item.seriesName.isNullOrBlank() && !previous?.seriesName.isNullOrBlank()) {
            item.copy(seriesName = previous?.seriesName)
        } else {
            item
        }

        val newEntryMap = _continueWatchingEntries.value.toMutableMap()
        newEntryMap[progressItem.contentId] = progressItem
        newEntryMap["${progressItem.contentType}:${progressItem.contentId}"] = progressItem
        val bareId = progressItem.contentId.substringAfterLast(":")
        newEntryMap["${progressItem.contentType}:$bareId"] = progressItem
        val normalizedKey = when (progressItem.contentType) {
            "series" -> progressItem.seriesName?.trim()?.lowercase()
            else -> progressItem.normalizedTitle.trim().lowercase()
                .ifBlank { progressItem.title.trim().lowercase() }
        }
        if (!normalizedKey.isNullOrBlank()) {
            newEntryMap["title:$normalizedKey"] = item
        }
        _continueWatchingEntries.value = newEntryMap

        val synthetic = buildContinueWatchingItem(progressItem, searchableSnapshot)
        newEntryMap[synthetic.stableId] = progressItem

        val currentCwItems = _continueWatchingSection.value?.items?.toMutableList() ?: mutableListOf()
        val existingIdx = currentCwItems.indexOfFirst {
            it.stableId == synthetic.stableId ||
                (progressItem.contentType == "series" && it.seriesName == progressItem.seriesName) ||
                (progressItem.contentType == "movie" && it.providerId == progressItem.contentId)
        }

        if (existingIdx >= 0) {
            currentCwItems[existingIdx] = synthetic
        } else {
            currentCwItems.add(synthetic)
        }

        val reorderedItems = currentCwItems
            .mapNotNull { cwItem ->
                val wp = _continueWatchingEntries.value[cwItem.stableId] ?: return@mapNotNull null
                wp to wp.lastWatchedAt
            }
            .sortedByDescending { it.second }
            .map { it.first }

        val catalogItems = reorderedItems.map { wp ->
            buildContinueWatchingItem(wp, searchableSnapshot).also { syn ->
                newEntryMap[syn.stableId] = wp
            }
        }
        _continueWatchingEntries.value = newEntryMap
        _continueWatchingSection.value = BrowseSection("Continuar viendo", catalogItems)
        rebuildHomeSections()
    }

    fun resetCatalogState() {
        _homeCatalog.value = null
        _homeSections.value = emptyList()
        _continueWatchingSection.value = null
        _continueWatchingEntries.value = emptyMap()
        _searchableItems.value = emptyList()
        _channelLineup.value = emptyList()
        _selectedHero.value = null
        _isLoaded.value = false
        _errorMessage.value = null
        _channelFilters.value = CatalogFilters()
        _movieFilters.value = CatalogFilters()
        _seriesFilters.value = CatalogFilters()
        _channelFilterCountry.value = null
        _movieFilterCountry.value = null
        _seriesFilterCountry.value = null
        _contentSyncState.value = ContentSyncState.IDLE
        _contentSyncError.value = null
        _currentSyncLabel.value = ""
        _currentSyncCount.value = 0
        _overallSyncProgress.value = 0f
    }
}
