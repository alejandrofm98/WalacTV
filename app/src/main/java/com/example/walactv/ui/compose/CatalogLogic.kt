package com.example.walactv.ui

import android.util.Log
import com.example.walactv.BrowseSection
import com.example.walactv.CatalogFilters
import com.example.walactv.CatalogItem
import com.example.walactv.CatalogMemory
import com.example.walactv.ComposeMainFragment
import com.example.walactv.ComposeMainFragment.Companion.TAG
import com.example.walactv.ComposeMainFragment.ContentSyncState
import com.example.walactv.ContentKind
import com.example.walactv.HomeCatalog
import com.example.walactv.PreferencesManager
import com.example.walactv.SearchFragment
import com.example.walactv.WatchProgressItem
import com.example.walactv.tmdbDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.walactv.R

// ── Content loading ────────────────────────────────────────────────────────

internal fun ComposeMainFragment.startLoad(forceRefresh: Boolean = false) {
    if (homeCatalog != null && !forceRefresh) {
        refreshEvents()
        return
    }

    scope.launch {
        errorMessage = null
        contentSyncState = ContentSyncState.CHECKING
        Log.d(TAG, "startLoad: beginning content sync (forceRefresh=$forceRefresh)")

        val token = runCatching { repository.getAccessToken() }.getOrNull() ?: ""

        val needsChannels = contentCacheManager.needsSyncChannels(token)

        if (!needsChannels) {
            contentSyncState = ContentSyncState.READY
            loadChannelFilters()
        } else {
            contentSyncState = ContentSyncState.SYNCING
            currentSyncLabel = ""
            currentSyncCount = 0
            overallSyncProgress = 0f

            val results = mutableListOf<Result<*>>()
            val totalSteps = 1

            currentSyncLabel = "Sincronizando canales"
            val r = contentCacheManager.syncChannels(token)
            results.add(r)
            overallSyncProgress = 1f / totalSteps
            currentSyncCount = r.getOrNull() as? Int ?: 0

            if (results.any { it.isFailure }) {
                contentSyncState = ContentSyncState.ERROR
                contentSyncError = "Error al sincronizar contenido"
            } else {
                currentSyncLabel = "Sincronización completada"
                currentSyncCount = 0
                overallSyncProgress = 1f
                contentSyncState = ContentSyncState.READY
                loadChannelFilters()
            }
        }

        runCatching { repository.loadHomeCatalog(forceRefresh = forceRefresh) }
            .onSuccess { catalog ->
                homeCatalog = catalog
                updateStateFromCatalog(catalog)
                isLoaded = true
            }
            .onFailure {
                if (!isLoaded) errorMessage = it.message ?: "Error al cargar la aplicacion"
            }
    }
}

internal fun ComposeMainFragment.refreshEvents() {
    if (!isSignedIn) return
    scope.launch {
        runCatching { repository.loadEventsOnly() }
            .onSuccess { catalog ->
                val eventSections = catalog.sections
                repository.updateHomeEventsCache(eventSections)
                homeCatalog = homeCatalog?.let { current ->
                    val nonEventSections = current.sections.filterNot { section ->
                        section.items.any { it.kind == ContentKind.EVENT }
                    }
                    current.copy(
                        sections = eventSections + nonEventSections,
                        searchableItems = (eventSections.flatMap(BrowseSection::items) + current.searchableItems.filterNot { it.kind == ContentKind.EVENT })
                            .distinctBy(CatalogItem::stableId),
                    )
                } ?: catalog
                updateStateFromCatalog(homeCatalog ?: catalog)
            }
            .onFailure { Log.w(TAG, "No se pudieron refrescar eventos", it) }
    }
}

internal fun ComposeMainFragment.loadChannelFilters() {
    scope.launch {
        runCatching { channelFilters = contentCacheManager.getLocalChannelFilters() }
            .onFailure { Log.e(TAG, "Error loading local channel filters", it) }
    }
}

internal fun ComposeMainFragment.updateStateFromCatalog(catalog: HomeCatalog) {
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

internal fun ComposeMainFragment.rebuildHomeSections() {
    val baseSections = homeCatalog?.sections.orEmpty()
    homeSections = continueWatchingSection?.let { cw ->
        if (baseSections.isEmpty()) listOf(cw)
        else listOf(baseSections.first()) + cw + baseSections.drop(1)
    } ?: baseSections
}

internal fun ComposeMainFragment.loadContinueWatching() {
    val requestVersion = ++continueWatchingRequestVersion
    val searchableSnapshot = searchableItems
    scope.launch {
        try {
            // Cargamos en paralelo: items en progreso + items vistos
            val inProgressItems = watchProgressRepo.getContinueWatching()
            val watchedItems = watchProgressRepo.getWatchedItems()

            // El entryMap incluye ambas listas
            val allItems = inProgressItems + watchedItems.filter { watched ->
                inProgressItems.none { it.contentId == watched.contentId }
            }

            val entryMap = mutableMapOf<String, WatchProgressItem>()

            // Indexamos todos (en progreso + vistos) para el lookup del badge
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
            continueWatchingEntries = entryMap

            // La sección "Continuar viendo" solo muestra los que están en progreso
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
                continueWatchingSection = BrowseSection("Continuar viendo", catalogItems)
            } else {
                continueWatchingSection = null
            }

            rebuildHomeSections()

        } catch (e: Exception) {
            Log.w(TAG, "Could not load continue watching[$requestVersion]: ${e.message}", e)
        }
    }
}

internal fun ComposeMainFragment.removeContinueWatchingLocally(
    contentId: String? = null,
    seriesName: String? = null,
) {
    continueWatchingEntries = continueWatchingEntries
        .filterValues { wp ->
            if (seriesName != null) wp.seriesName != seriesName
            else wp.contentId != contentId
        }
    continueWatchingSection = continueWatchingSection?.let { section ->
        val filtered = section.items.filter { item ->
            if (seriesName != null) item.seriesName != seriesName
            else item.providerId != contentId
        }
        if (filtered.isEmpty()) null else section.copy(items = filtered)
    }
    rebuildHomeSections()
}

internal suspend fun ComposeMainFragment.deleteAllSeriesProgress(seriesName: String) {
    try {
        coroutineScope {
            continueWatchingEntries
                .filter { (_, wp) -> wp.seriesName == seriesName }
                .map { (_, wp) -> async { watchProgressRepo.deleteProgress(wp.contentId) } }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error deleting series progress for $seriesName", e)
    }
}

// ── Continue-watching item builder ─────────────────────────────────────────

internal fun ComposeMainFragment.buildContinueWatchingItem(
    wp: WatchProgressItem,
    searchableSnapshot: List<CatalogItem>,
): CatalogItem {
    val kind = if (wp.contentType == "series") ContentKind.SERIES else ContentKind.MOVIE
    val subtitle = if (wp.contentType == "series") buildEpisodeLabel(wp.seasonNumber, wp.episodeNumber) else ""
    val fallbackTitle = wp.normalizedTitle.cleanDisplayText()
        .ifBlank { wp.seriesName.cleanDisplayText() }
        .ifBlank { wp.title.cleanDisplayText() }
    val fallbackStableId = if (wp.contentType == "series") "cw_series:${wp.contentId}" else "cw_movie:${wp.contentId}"

    val homeSnapshot = homeSections.asSequence().flatMap { it.items.asSequence() }.toList()
    val richSnapshot = homeSnapshot + searchableSnapshot

    val matched = when (wp.contentType) {
        "movie"  -> richSnapshot.firstOrNull { it.kind == ContentKind.MOVIE && it.matchesByProviderId(wp.contentId) }
        "series" -> findSeriesMatch(wp, richSnapshot)
            ?: richSnapshot.firstOrNull { it.kind == ContentKind.SERIES && it.matchesByProviderId(wp.contentId) }
        else     -> null
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

private fun buildTmdbImageUrl(path: String?, size: String): String? {
    val cleanPath = path.cleanDisplayText()
    if (cleanPath.isBlank()) return null
    if (cleanPath.startsWith("http://") || cleanPath.startsWith("https://")) return cleanPath
    val normalizedPath = if (cleanPath.startsWith("/")) cleanPath else "/$cleanPath"
    return "https://image.tmdb.org/t/p/$size$normalizedPath"
}

internal fun String?.cleanDisplayText(): String =
    this?.takeUnless { it.equals("null", ignoreCase = true) }?.trim().orEmpty()

internal fun CatalogItem.matchesByProviderId(contentId: String): Boolean {
    val itemId = contentId.substringAfterLast(":")
    return providerId == itemId || stableId == contentId || stableId.endsWith(":$itemId")
}

internal fun ComposeMainFragment.findSeriesMatch(
    wp: WatchProgressItem,
    items: List<CatalogItem> = searchableItems,
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

internal fun buildEpisodeLabel(season: Int?, episode: Int?): String {
    val s = season?.let { "S%02d".format(it) } ?: ""
    val e = episode?.let { "E%02d".format(it) } ?: ""
    return s + e
}

// ── Filters ────────────────────────────────────────────────────────────────

internal fun ComposeMainFragment.ensureFiltersLoaded(kind: ContentKind, country: String? = null) {
    scope.launch {
        runCatching { repository.loadCatalogFilters(kind, country) }
            .onSuccess { filters ->
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

internal suspend fun ComposeMainFragment.ensureFiltersLoadedAwait(kind: ContentKind, country: String? = null) {
    runCatching { withContext(Dispatchers.IO) { repository.loadCatalogFilters(kind, country) } }
        .onSuccess { filters ->
            when (kind) {
                ContentKind.CHANNEL -> { channelFilters = filters; channelFilterCountry = country }
                ContentKind.MOVIE   -> { movieFilters = filters; movieFilterCountry = country }
                ContentKind.SERIES  -> { seriesFilters = filters; seriesFilterCountry = country }
                ContentKind.EVENT   -> Unit
            }
        }
        .onFailure { Log.e(TAG, "ensureFiltersLoadedAwait FAILED for $kind", it) }
}

// ── Auth ───────────────────────────────────────────────────────────────────

internal fun ComposeMainFragment.performSignIn() {
    loginError = null
    isSigningIn = true
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { repository.signIn(loginUsername, loginPassword) } }
            .onSuccess { resetCatalogState(); isSignedIn = true; isSigningIn = false; startLoad() }
            .onFailure { isSigningIn = false; loginError = it.message ?: "No se pudo iniciar sesion" }
    }
}

internal fun ComposeMainFragment.performSignOut() {
    repository.signOut()
    resetCatalogState()
    isSignedIn = false; loginUsername = ""; loginPassword = ""; loginError = null
}

internal fun ComposeMainFragment.resetCatalogState() {
    homeCatalog = null; homeSections = emptyList(); continueWatchingSection = null
    continueWatchingEntries = emptyMap(); searchableItems = emptyList(); channelLineup = emptyList()
    selectedHero = null; isLoaded = false; errorMessage = null; currentItem = null
    currentStreamIndex = 0; activePlaybackLineup = emptyList()
    currentMode = ComposeMainFragment.MainMode.Home
    channelFilters = CatalogFilters(); movieFilters = CatalogFilters(); seriesFilters = CatalogFilters()
    channelFilterCountry = null; movieFilterCountry = null; seriesFilterCountry = null
    contentSyncState = ContentSyncState.IDLE; contentSyncError = null
    currentSyncLabel = ""; currentSyncCount = 0; overallSyncProgress = 0f
}

// ── Mode / navigation helpers ──────────────────────────────────────────────

internal fun ComposeMainFragment.changeMode(newMode: ComposeMainFragment.MainMode) {
    if (currentMode == newMode) return
    currentMode = newMode
    selectedHero = defaultItemForMode(newMode)
    when (newMode) {
        ComposeMainFragment.MainMode.TV     -> ensureFiltersLoaded(ContentKind.CHANNEL)
        ComposeMainFragment.MainMode.Movies -> ensureFiltersLoaded(ContentKind.MOVIE)
        ComposeMainFragment.MainMode.Series -> ensureFiltersLoaded(ContentKind.SERIES)
        else -> Unit
    }
}

internal fun ComposeMainFragment.rememberPlaybackReturnState(item: CatalogItem) {
    Log.d(TAG, "TMDB_RETURN remember mode=$currentMode item=${item.tmdbDebug()}")
    playbackReturnState = ComposeMainFragment.PlaybackReturnState(
        mode = currentMode,
        selectedItemStableId = item.stableId,
        selectedItemSnapshot = item,
    )
}

internal fun ComposeMainFragment.defaultItemForMode(
    mode: ComposeMainFragment.MainMode,
): CatalogItem? = when (mode) {
    ComposeMainFragment.MainMode.Home     -> homeSections.firstNotNullOfOrNull { it.items.firstOrNull() }
    ComposeMainFragment.MainMode.TV       -> searchableItems.firstOrNull { it.kind == ContentKind.CHANNEL }
    ComposeMainFragment.MainMode.Events   -> searchableItems.firstOrNull { it.kind == ContentKind.EVENT }
    ComposeMainFragment.MainMode.Movies   -> searchableItems.firstOrNull { it.kind == ContentKind.MOVIE }
    ComposeMainFragment.MainMode.Series   -> searchableItems.firstOrNull { it.kind == ContentKind.SERIES }
    ComposeMainFragment.MainMode.Settings -> null
}

internal fun ComposeMainFragment.refreshCatalog() {
    homeCatalog = null; homeSections = emptyList(); continueWatchingSection = null
    continueWatchingEntries = emptyMap(); searchableItems = emptyList(); channelLineup = emptyList()
    activePlaybackLineup = emptyList(); selectedHero = null; isLoaded = false
    repository.clearHomeMemoryCache()
    startLoad(forceRefresh = true)
}

internal fun ComposeMainFragment.openSearch() {
    isRailExpanded = false
    requireActivity().supportFragmentManager.beginTransaction()
        .replace(R.id.main_browse_fragment, SearchFragment.newInstance(searchableItems))
        .addToBackStack("SearchFragment")
        .commit()
}

internal fun screenTitle(kind: ContentKind) = when (kind) {
    ContentKind.EVENT   -> "Eventos"
    ContentKind.CHANNEL -> "TV en directo"
    ContentKind.MOVIE   -> "Peliculas"
    ContentKind.SERIES  -> "Series"
}

internal fun kindLabel(kind: ContentKind) = when (kind) {
    ContentKind.EVENT   -> "Evento"
    ContentKind.CHANNEL -> "Canal"
    ContentKind.MOVIE   -> "Pelicula"
    ContentKind.SERIES  -> "Serie"
}

internal fun ComposeMainFragment.findNextEventIndex(items: List<CatalogItem>): Int {
    val now = java.util.Calendar.getInstance()
    var bestUpcoming = -1; var bestUpcomingDelta = Long.MAX_VALUE
    var bestLive = -1; var bestLiveDelta = Long.MAX_VALUE
    for (i in items.indices) {
        if (items[i].kind != ContentKind.EVENT) continue
        val parsed = runCatching { ComposeMainFragment.EVENT_TIME_FORMAT.parse(items[i].badgeText) }.getOrNull() ?: continue
        val eventCal = java.util.Calendar.getInstance().apply {
            time = parsed
            set(java.util.Calendar.YEAR, now.get(java.util.Calendar.YEAR))
            set(java.util.Calendar.MONTH, now.get(java.util.Calendar.MONTH))
            set(java.util.Calendar.DAY_OF_MONTH, now.get(java.util.Calendar.DAY_OF_MONTH))
        }
        val delta = (now.timeInMillis - eventCal.timeInMillis) / 60_000L
        when {
            delta < 0 && -delta < bestUpcomingDelta -> { bestUpcoming = i; bestUpcomingDelta = -delta }
            delta in 0..180 && delta < bestLiveDelta -> { bestLive = i; bestLiveDelta = delta }
        }
    }
    return if (bestUpcoming >= 0) bestUpcoming else bestLive
}

internal fun ComposeMainFragment.upsertContinueWatchingEntry(item: WatchProgressItem) {
    val searchableSnapshot = searchableItems
    val previous = continueWatchingEntries[item.contentId]
        ?: continueWatchingEntries["${item.contentType}:${item.contentId}"]
        ?: continueWatchingEntries[item.contentId.substringAfterLast(":")]
    val progressItem = if (item.contentType == "series" && item.seriesName.isNullOrBlank() && !previous?.seriesName.isNullOrBlank()) {
        item.copy(seriesName = previous?.seriesName)
    } else {
        item
    }
    
    // Agregar/actualizar en continueWatchingEntries
    val newEntryMap = continueWatchingEntries.toMutableMap()
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
    continueWatchingEntries = newEntryMap
    
    // Crear o actualizar la card en continueWatchingSection
    val existingCwSection = continueWatchingSection
    val synthetic = buildContinueWatchingItem(progressItem, searchableSnapshot)
    newEntryMap[synthetic.stableId] = progressItem
    
    val currentCwItems = continueWatchingSection?.items?.toMutableList() ?: mutableListOf()
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
    
    // Reordenar por lastWatchedAt
    val reorderedItems = currentCwItems
        .mapNotNull { cwItem ->
            val wp = continueWatchingEntries[cwItem.stableId] ?: return@mapNotNull null
            wp to wp.lastWatchedAt
        }
        .sortedByDescending { it.second }
        .map { it.first }
    
    // Rebuild section with sorted items
    val catalogItems = reorderedItems.map { wp ->
        buildContinueWatchingItem(wp, searchableSnapshot).also { synthetic ->
            newEntryMap[synthetic.stableId] = wp
        }
    }
    continueWatchingSection = BrowseSection("Continuar viendo", catalogItems)
    rebuildHomeSections()
}
