package com.example.walactv.ui.compose

import android.util.Log
import com.example.walactv.data.model.BrowseSection
import com.example.walactv.data.model.CatalogFilters
import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.model.CatalogMemory
import com.example.walactv.ui.fragment.ComposeMainFragment
import com.example.walactv.ui.fragment.ComposeMainFragment.Companion.TAG
import com.example.walactv.ui.fragment.ComposeMainFragment.ContentSyncState
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.model.HomeCatalog
import com.example.walactv.ui.fragment.SearchFragment
import com.example.walactv.data.remote.api.dto.WatchProgressDto
import com.example.walactv.ui.fragment.tmdbDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.walactv.R

// ── Content loading (delegates to ViewModel) ────────────────────────────────

internal fun ComposeMainFragment.startLoad(forceRefresh: Boolean = false) {
    viewModel.startLoad(forceRefresh)
}

internal fun ComposeMainFragment.refreshEvents() {
    if (!isSignedIn) return
    viewModel.refreshEvents()
}

internal fun ComposeMainFragment.loadChannelFilters() {
    viewModel.loadChannelFilters()
}

internal fun ComposeMainFragment.updateStateFromCatalog(catalog: HomeCatalog) {
    viewModel.updateStateFromCatalog(catalog)
}

internal fun ComposeMainFragment.loadContinueWatching() {
    viewModel.loadContinueWatching()
}

internal fun ComposeMainFragment.removeContinueWatchingLocally(
    contentId: String? = null,
    seriesName: String? = null,
) {
    viewModel.removeContinueWatchingLocally(contentId, seriesName)
}

internal suspend fun ComposeMainFragment.deleteAllSeriesProgress(seriesName: String) {
    try {
        coroutineScope {
            continueWatchingEntries
                .filter { (_, wp) -> wp.seriesName == seriesName }
                .map { (_, wp) -> async { watchProgressRepo.deleteProgress((wp.contentId ?: "").substringAfterLast(":")) } }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error deleting series progress for $seriesName", e)
    }
}

// ── Continue-watching item builder ─────────────────────────────────────────

internal fun ComposeMainFragment.buildContinueWatchingItem(
    wp: WatchProgressDto,
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
        "movie"  -> richSnapshot.firstOrNull { it.kind == ContentKind.MOVIE && it.matchesByProviderId(wp.contentId.orEmpty()) }
        "series" -> findSeriesMatch(wp, richSnapshot)
            ?: richSnapshot.firstOrNull { it.kind == ContentKind.SERIES && it.matchesByProviderId(wp.contentId.orEmpty()) }
        else     -> null
    }

    Log.d(
        TAG,
        "TMDB_CW build contentId=${wp.contentId} type=${wp.contentType} series=${wp.seriesName} matched=${matched.tmdbDebug()} " +
            "wpTmdb=${wp.tmdbTitle.orEmpty()} wpBackdrop=${wp.backdropPath.orEmpty()} wpPoster=${wp.posterPath.orEmpty()} wpImage=${(wp.imageUrl ?: "").take(80)}",
    )

    return matched?.copy(
        stableId = fallbackStableId,
        providerId = wp.contentId,
        title = fallbackTitle,
        normalizedTitle = null,
        subtitle = subtitle,
        description = matched.description.cleanDisplayText().ifBlank { wp.title.orEmpty() },
        imageUrl = matched.imageUrl.ifBlank { wp.imageUrl.orEmpty() },
        seriesName = matched.seriesName.cleanDisplayText().ifBlank { wp.seriesName.orEmpty() }.ifBlank { null },
    ) ?: wp.toCatalogItemFallback(
        stableId = fallbackStableId,
        subtitle = subtitle,
        imageUrl = wp.imageUrl.orEmpty(),
        kind = kind,
        title = fallbackTitle,
    )
}

private fun WatchProgressDto.toCatalogItemFallback(
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
        description = overview.cleanDisplayText().ifBlank { this.title.orEmpty() },
        imageUrl = imageUrl.ifBlank { tmdbPosterUrl.orEmpty() },
        kind = kind,
        group = "Continuar viendo",
        badgeText = if (kind == ContentKind.MOVIE) "Pelicula" else "Serie",
        seriesName = seriesName,
        seriesProviderId = seriesProviderId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        // Necesario para que el detalle consulte Torrentio (Fuentes/playback)
        imdbId = imdbId,
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

internal fun buildTmdbImageUrl(path: String?, size: String): String? {
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
    wp: WatchProgressDto,
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
    val s = season?.let { "T${it}" } ?: ""
    val e = episode?.let { "E${it}" } ?: ""
    return if (s.isNotBlank() && e.isNotBlank()) "$s · $e" else s + e
}

internal fun formatDurationRemaining(positionMs: Long, durationMs: Long): String? {
    if (durationMs <= 0 || positionMs <= 0) return null
    val remainingMs = durationMs - positionMs
    if (remainingMs <= 0) return null
    val totalSeconds = remainingMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m restantes"
        minutes > 0 -> "${minutes}m restantes"
        else -> null
    }
}

// ── Filters (delegates to ViewModel) ────────────────────────────────────────

internal fun ComposeMainFragment.ensureFiltersLoaded(kind: ContentKind, country: String? = null) {
    if (kind == ContentKind.CHANNEL) {
        viewModel.loadChannelFilters()
    } else {
        viewModel.ensureFiltersLoaded(kind, country)
    }
}

internal suspend fun ComposeMainFragment.ensureFiltersLoadedAwait(kind: ContentKind, country: String? = null) {
    runCatching { withContext(Dispatchers.IO) { repository.loadCatalogFilters(kind, country) } }
        .onSuccess { filters ->
            when (kind) {
                ContentKind.CHANNEL -> { viewModel._channelFilters.value = filters; viewModel._channelFilterCountry.value = country }
                ContentKind.MOVIE   -> { viewModel._movieFilters.value = filters; viewModel._movieFilterCountry.value = country }
                ContentKind.SERIES  -> { viewModel._seriesFilters.value = filters; viewModel._seriesFilterCountry.value = country }
                ContentKind.EVENT   -> Unit
                ContentKind.UFC     -> Unit
            }
        }
        .onFailure { Log.e(TAG, "ensureFiltersLoadedAwait FAILED for $kind", it) }
}

// ── Auth ───────────────────────────────────────────────────────────────────

internal fun ComposeMainFragment.performSignIn() {
    loginError = null
    isSigningIn = true
    scope.launch {
        viewModel.signIn(loginUsername, loginPassword)
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
    viewModel.resetCatalogState()
    currentItem = null
    currentStreamIndex = 0
    activePlaybackLineup = emptyList()
    currentMode = ComposeMainFragment.MainMode.Home
}

// ── Mode / navigation helpers ──────────────────────────────────────────────

internal fun ComposeMainFragment.changeMode(newMode: ComposeMainFragment.MainMode) {
    if (currentMode == newMode) return
    currentMode = newMode
    selectedHero = defaultItemForMode(newMode)
    when (newMode) {
        ComposeMainFragment.MainMode.TV       -> ensureFiltersLoaded(ContentKind.CHANNEL)
        ComposeMainFragment.MainMode.Discover -> {
            ensureFiltersLoaded(ContentKind.MOVIE)
            ensureFiltersLoaded(ContentKind.SERIES)
            ensureFiltersLoaded(ContentKind.UFC)
        }
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
    ComposeMainFragment.MainMode.Discover -> searchableItems.firstOrNull { it.kind == ContentKind.MOVIE || it.kind == ContentKind.SERIES }
    ComposeMainFragment.MainMode.Settings -> null
}

internal fun ComposeMainFragment.openSearch() {
    isRailExpanded = false
    requireActivity().supportFragmentManager.beginTransaction()
        .add(R.id.main_browse_fragment, SearchFragment.newInstance(searchableItems))
        .addToBackStack("SearchFragment")
        .commit()
}

internal fun screenTitle(kind: ContentKind) = when (kind) {
    ContentKind.EVENT   -> "Eventos"
    ContentKind.CHANNEL -> "TV en directo"
    ContentKind.MOVIE   -> "Peliculas"
    ContentKind.SERIES  -> "Series"
    ContentKind.UFC     -> "UFC"
}

internal fun ComposeMainFragment.findNextEventIndex(items: List<CatalogItem>): Int {
    val now = java.util.Calendar.getInstance()
    var bestUpcoming = -1; var bestUpcomingDelta = Long.MAX_VALUE
    var bestLive = -1; var bestLiveDelta = Long.MAX_VALUE
    var bestPast = -1; var bestPastDelta = Long.MAX_VALUE
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
            delta in 0..300 && delta < bestLiveDelta -> { bestLive = i; bestLiveDelta = delta }
            delta > 300 && delta < bestPastDelta -> { bestPast = i; bestPastDelta = delta }
        }
    }
    return bestUpcoming.takeIf { it >= 0 }
        ?: bestLive.takeIf { it >= 0 }
        ?: bestPast
}

internal fun ComposeMainFragment.upsertContinueWatchingEntry(item: WatchProgressDto) {
    Log.d("CW_UPSERT", "called: contentId=${item.contentId} contentType=${item.contentType} title=${item.title} position=${item.positionMs} seriesName=${item.seriesName}")
    val searchableSnapshot = searchableItems
    val previous = continueWatchingEntries[item.contentId.orEmpty()]
        ?: continueWatchingEntries["${item.contentType}:${item.contentId}"]
        ?: continueWatchingEntries[(item.contentId ?: "").substringAfterLast(":")]
    val progressItem = if (item.contentType == "series" && item.seriesName.isNullOrBlank() && !previous?.seriesName.isNullOrBlank()) {
        item.copy(seriesName = previous?.seriesName)
    } else {
        item
    }
    
    // Agregar/actualizar en continueWatchingEntries
    val newEntryMap = continueWatchingEntries.toMutableMap()
    newEntryMap[progressItem.contentId.orEmpty()] = progressItem
    newEntryMap["${progressItem.contentType}:${progressItem.contentId}"] = progressItem
    val bareId = (progressItem.contentId ?: "").substringAfterLast(":")
    newEntryMap["${progressItem.contentType}:$bareId"] = progressItem
    val normalizedKey = when (progressItem.contentType) {
        "series" -> progressItem.seriesName?.trim()?.lowercase()
        else -> (progressItem.normalizedTitle ?: "").trim().lowercase()
            .ifBlank { (progressItem.title ?: "").trim().lowercase() }
    }
    if (!normalizedKey.isNullOrBlank()) {
        newEntryMap["title:$normalizedKey"] = item
    }
    continueWatchingEntries = newEntryMap
    
    // Crear o actualizar la card en continueWatchingSection
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
            wp to (wp.lastWatchedAt ?: "")
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
}
