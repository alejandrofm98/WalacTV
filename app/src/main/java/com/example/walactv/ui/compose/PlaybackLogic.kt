package com.example.walactv.ui

import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import com.example.walactv.CatalogItem
import com.example.walactv.CatalogMemory
import com.example.walactv.ComposeMainFragment
import com.example.walactv.ContentKind
import com.example.walactv.PlayerFragment
import com.example.walactv.PreferencesManager
import com.example.walactv.R
import com.example.walactv.SeriesDetailFragment
import com.example.walactv.StreamOption
import com.example.walactv.WatchProgressItem
import com.example.walactv.idioma
import com.example.walactv.normalizeLanguageCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PlaybackLogic"

// ── Card click dispatcher ──────────────────────────────────────────────────

internal fun ComposeMainFragment.handleCardClick(item: CatalogItem, lineup: List<CatalogItem> = emptyList()) {
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

// ── Continue watching openers ──────────────────────────────────────────────

internal fun ComposeMainFragment.openContinueWatchingItem(cardItem: CatalogItem, progress: WatchProgressItem) {
    scope.launch {
        when (progress.contentType) {
            "movie"  -> openContinueWatchingMovie(cardItem, progress)
            "series" -> openContinueWatchingSeries(cardItem, progress)
            else     -> Log.w(TAG, "Unsupported continue watching type: ${progress.contentType}")
        }
    }
}

private suspend fun ComposeMainFragment.openContinueWatchingMovie(cardItem: CatalogItem, progress: WatchProgressItem) {
    val item = repository.fetchContentItem(ContentKind.MOVIE, progress.contentId)
    if (item == null) {
        withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "No se pudo abrir la pelicula", Toast.LENGTH_SHORT).show() }
        return
    }
    withContext(Dispatchers.Main) {
        activePlaybackLineup = emptyList()
        playResolvedCatalogItem(item, 0)
        // Sobrescribir con cardItem CW para que vuelva a la card correcta
        rememberPlaybackReturnState(cardItem)
    }
}

private suspend fun ComposeMainFragment.openContinueWatchingSeries(
    cardItem: CatalogItem,
    progress: WatchProgressItem,
) {
    val episode = repository.fetchContentItem(ContentKind.SERIES, progress.contentId)
    if (episode == null) {
        withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "No se pudo abrir la serie", Toast.LENGTH_SHORT).show() }
        return
    }
    val seriesName = episode.seriesName ?: progress.seriesName ?: cardItem.seriesName ?: cardItem.title
    val allEpisodes = repository.loadSeriesEpisodes(seriesName)
    val logicalEpisodes = allEpisodes
        .groupBy { Triple(it.seriesName, it.seasonNumber, it.episodeNumber) }
        .values
        .mapNotNull { variants ->
            variants.firstOrNull { normalizeLanguageCode(it.idioma) == normalizeLanguageCode(PreferencesManager.getPreferredLanguageOrDefault()) }
                ?: variants.firstOrNull()
        }
        .sortedWith(compareBy({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }))

    val currentIndex = logicalEpisodes.indexOfFirst {
        it.seriesName == episode.seriesName && it.seasonNumber == episode.seasonNumber && it.episodeNumber == episode.episodeNumber
    }

    val nextEpisodeCallback: (() -> Unit)? = if (currentIndex >= 0 && currentIndex < logicalEpisodes.lastIndex) {
        {
            openContinueWatchingItem(cardItem, progress.copy(
                contentId = logicalEpisodes[currentIndex + 1].providerId ?: logicalEpisodes[currentIndex + 1].stableId,
                seasonNumber = logicalEpisodes[currentIndex + 1].seasonNumber,
                episodeNumber = logicalEpisodes[currentIndex + 1].episodeNumber,
                seriesName = logicalEpisodes[currentIndex + 1].seriesName,
                title = logicalEpisodes[currentIndex + 1].title,
                imageUrl = logicalEpisodes[currentIndex + 1].imageUrl,
            ))
        }
    } else null

    val previousEpisodeCallback: (() -> Unit)? = if (currentIndex > 0) {
        {
            openContinueWatchingItem(cardItem, progress.copy(
                contentId = logicalEpisodes[currentIndex - 1].providerId ?: logicalEpisodes[currentIndex - 1].stableId,
                seasonNumber = logicalEpisodes[currentIndex - 1].seasonNumber,
                episodeNumber = logicalEpisodes[currentIndex - 1].episodeNumber,
                seriesName = logicalEpisodes[currentIndex - 1].seriesName,
                title = logicalEpisodes[currentIndex - 1].title,
                imageUrl = logicalEpisodes[currentIndex - 1].imageUrl,
            ))
        }
    } else null

    val stream = episode.streamOptions.firstOrNull() ?: run {
        withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "No hay streams disponibles", Toast.LENGTH_SHORT).show() }
        return
    }

    withContext(Dispatchers.Main) {
        val playerFragment = PlayerFragment()
        playerFragment.initialize(
            streamUrl = stream.url, overlayNumber = episode.kind.name, overlayTitle = episode.title,
            overlayMeta = episode.description.ifBlank { stream.label }, contentKind = episode.kind,
            onNavigateChannel = { false }, onNavigateOption = { false }, onDirectChannelNumber = { false },
            onToggleFavorite = { false }, onOpenFavorites = { false }, onOpenRecents = { false },
            onNextEpisode = nextEpisodeCallback,
            onPreviousEpisode = previousEpisodeCallback,
            allSeriesEpisodes = allEpisodes, currentEpisode = episode,
            overlayLogoUrl = episode.imageUrl, contentId = episode.providerId ?: progress.contentId,
            onPlayerClosed = { restorePlaybackReturnState(); restoreFocusAfterPlayer() },
            onProgressSaved = { item -> upsertContinueWatchingEntry(item) },
        )
        rememberPlaybackReturnState(cardItem)
        currentItem = cardItem
        currentStreamIndex = 0
        launchPlayerFragment(playerFragment)
    }
}

// ── Core playback ──────────────────────────────────────────────────────────

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal fun ComposeMainFragment.playCatalogItem(item: CatalogItem, optionIndex: Int, showOptionsOnStart: Boolean = false) {
    if (item.kind == ContentKind.EVENT) {
        scope.launch {
            val resolved = repository.resolveEventItem(item)
            if (resolved.streamOptions.isEmpty()) {
                Toast.makeText(requireContext(), R.string.no_streams_available, Toast.LENGTH_SHORT).show()
                return@launch
            }
            playResolvedCatalogItem(resolved, optionIndex.coerceIn(resolved.streamOptions.indices), showOptionsOnStart)
        }
        return
    }
    playResolvedCatalogItem(item, optionIndex, showOptionsOnStart)
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal fun ComposeMainFragment.playResolvedCatalogItem(item: CatalogItem, optionIndex: Int, showOptionsOnStart: Boolean = false) {
    val stream = item.streamOptions.getOrNull(optionIndex) ?: return
    rememberPlaybackReturnState(item)
    currentItem = item
    currentStreamIndex = optionIndex
    if (item.kind == ContentKind.CHANNEL) channelStateStore.markRecent(item)

    val channelItem = resolveChannelFromEvent(item, stream)
    val favoriteTarget = channelItem ?: item

    val playerFragment = PlayerFragment()
    playerFragment.initialize(
        streamUrl = stream.url,
        overlayNumber = when {
            item.kind == ContentKind.CHANNEL && item.channelNumber != null -> "CH ${item.channelNumber}"
            item.kind == ContentKind.EVENT -> "EN DIRECTO"
            else -> item.kind.name
        },
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
        showOptionsOnStart = showOptionsOnStart,
        onSelectQuality = if (item.kind == ContentKind.MOVIE || item.kind == ContentKind.SERIES) {
            { newIndex -> playCatalogItem(item, newIndex) }
        } else null,
        overlayLogoUrl = item.imageUrl,
        isFavorite = channelStateStore.isFavorite(favoriteTarget),
        contentId = item.providerId ?: item.stableId,
        onPlayerClosed = { restorePlaybackReturnState(); restoreFocusAfterPlayer() },
        onProgressSaved = if (item.kind == ContentKind.MOVIE || item.kind == ContentKind.SERIES) {
            { progressItem -> upsertContinueWatchingEntry(progressItem) }
        } else null,
        customHeaders = stream.headers,
    )
    launchPlayerFragment(playerFragment)
}

internal fun ComposeMainFragment.launchPlayerFragment(playerFragment: PlayerFragment) {
    val fm = requireActivity().supportFragmentManager
    fm.findFragmentById(R.id.player_container)?.let { fm.beginTransaction().remove(it).commitNow() }
    fm.beginTransaction().replace(R.id.player_container, playerFragment, "player_fragment").commitNow()
    val container = requireActivity().findViewById<FrameLayout>(R.id.player_container)
    container.visibility = View.VISIBLE
    container.isFocusable = true
    container.isFocusableInTouchMode = true
    runCatching { container.requestFocus() }
}

// ── Channel navigation ─────────────────────────────────────────────────────

internal fun ComposeMainFragment.navigateChannel(direction: Int) {
    val current = currentItem ?: return
    val lineup = activePlaybackLineup.ifEmpty { channelLineup }
    val idx = lineup.indexOfFirst { it.stableId == current.stableId }.takeIf { it != -1 } ?: return
    val target = idx + direction
    if (target !in lineup.indices) return
    playCatalogItem(lineup[target], 0)
}

internal fun ComposeMainFragment.navigateOption(direction: Int) {
    val item = currentItem ?: return
    val newIndex = currentStreamIndex + direction
    if (newIndex !in item.streamOptions.indices) return
    playCatalogItem(item, newIndex, showOptionsOnStart = true)
}

internal fun ComposeMainFragment.navigateToChannelNumber(number: Int): Boolean {
    val match = channelLineup.firstOrNull { it.channelNumber == number } ?: return false
    playCatalogItem(match, 0)
    return true
}

internal fun ComposeMainFragment.toggleFavorite(item: CatalogItem): Boolean {
    CatalogMemory.registerChannel(item)
    val result = channelStateStore.toggleFavorite(item)
    rebuildHomeSections()
    scope.launch {
        runCatching { repository.updateChannelFavorite(item, result) }
            .onFailure {
                channelStateStore.setFavorite(item, !result)
                rebuildHomeSections()
                Toast.makeText(requireContext(), "No se pudo actualizar favoritos", Toast.LENGTH_SHORT).show()
            }
    }
    return result
}

internal fun ComposeMainFragment.resolveChannelFromEvent(item: CatalogItem, stream: StreamOption?): CatalogItem? {
    if (item.kind != ContentKind.EVENT) return null
    val providerId = stream?.providerId ?: return null
    if (providerId.isBlank()) return null
    val stableId = "channel:$providerId"
    val resolved = channelLineup.firstOrNull { it.stableId == stableId } ?: CatalogMemory.channelRegistry[stableId]
    if (resolved != null) return resolved
    val channelName = stream.label.split(" · ").firstOrNull() ?: item.title
    val fallback = CatalogItem(
        stableId = stableId, providerId = providerId, title = channelName, subtitle = "",
        description = item.description, imageUrl = item.imageUrl, kind = ContentKind.CHANNEL,
        group = item.group, badgeText = item.badgeText, streamOptions = item.streamOptions,
    )
    CatalogMemory.registerChannel(fallback)
    return fallback
}

internal fun ComposeMainFragment.openFavoriteChannel(): Boolean {
    val ids = channelStateStore.favoriteIds()
    val match = channelLineup.firstOrNull { ids.contains(it.stableId) } ?: return false
    playCatalogItem(match, 0)
    return true
}

internal fun ComposeMainFragment.openRecentChannel(): Boolean {
    val byId = channelLineup.associateBy(CatalogItem::stableId)
    val match = channelStateStore.recentIds().drop(1).mapNotNull(byId::get).firstOrNull()
        ?: channelStateStore.recentIds().mapNotNull(byId::get).firstOrNull() ?: return false
    playCatalogItem(match, 0)
    return true
}

internal fun ComposeMainFragment.openGuideOverlay(initialGroup: String?) {
    val fm = requireActivity().supportFragmentManager
    val playerFragment = fm.findFragmentByTag("player_fragment") as? PlayerFragment
    playerFragment?.closeFromHost()
    playerFragment?.let { fm.beginTransaction().remove(it).commitNow() }
    requireActivity().findViewById<FrameLayout>(R.id.player_container)?.visibility = View.GONE
    if (initialGroup != null) guideInitialGroup = initialGroup
    scope.launch {
        ensureFiltersLoadedAwait(ContentKind.CHANNEL)
        currentMode = ComposeMainFragment.MainMode.TV
        selectedHero = defaultItemForMode(ComposeMainFragment.MainMode.TV)
        restoreFocusAfterPlayer()
    }
}
