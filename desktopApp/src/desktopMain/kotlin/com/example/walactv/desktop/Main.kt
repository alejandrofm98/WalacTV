package com.example.walactv.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.walactv.shared.data.*
import com.example.walactv.shared.di.allPlatformModules
import com.example.walactv.shared.di.IPTV_BASE_URL
import com.example.walactv.shared.domain.CatalogItem
import com.example.walactv.shared.domain.ContentKind
import com.example.walactv.shared.domain.FormFactor
import com.example.walactv.shared.domain.FormFactorDetector
import com.example.walactv.shared.domain.HomeViewModel
import com.example.walactv.shared.ui.App
import com.example.walactv.shared.ui.components.AdaptiveNavigationRail
import com.example.walactv.shared.ui.components.DesktopVideoSurface
import com.example.walactv.shared.ui.components.NavItem
import com.example.walactv.shared.ui.screens.*
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun main() {
    FormFactorDetector.current = FormFactor.DESKTOP

    val iptvUrl = System.getProperty("iptv.base.url")
        .takeIf { it != null && it.isNotBlank() && !it.contains("example.invalid") }
        ?: BuildConfig.IPTV_BASE_URL
            .takeIf { it.isNotBlank() && !it.contains("example.invalid") }
        ?: "http://localhost:3010"

    startKoin {
        modules(allPlatformModules + module {
            single(IPTV_BASE_URL) { iptvUrl }
        })
    }

    application {
        val koin = GlobalContext.get()
        val scope = rememberCoroutineScope()

        val credentialStore = remember { koin.get<CredentialStore>() }
        val preferencesManager = remember { koin.get<PreferencesManager>() }
        val repository = remember { koin.get<IptvRepository>() }
        val watchProgressRepo = remember { koin.get<WatchProgressRepository>() }
        val channelStateStore = remember { koin.get<ChannelStateStore>() }
        val contentCacheManager = remember { koin.get<ContentCacheManager>() }

        val viewModel = remember {
            HomeViewModel(
                repository = repository,
                watchProgressRepo = watchProgressRepo,
                credentialStore = credentialStore,
                preferencesManager = preferencesManager,
                channelStateStore = channelStateStore,
                contentCacheManager = contentCacheManager,
                scope = scope,
            )
        }

        val isLoggedIn by viewModel.isLoggedIn.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val errorMessage by viewModel.errorMessage.collectAsState()
        val homeSections by viewModel.homeSections.collectAsState()
        val continueWatching by viewModel.continueWatchingSection.collectAsState()
        val selectedHero by viewModel.selectedHero.collectAsState()
        val searchableItems by viewModel.searchableItems.collectAsState()
        val discoverItems by viewModel.discoverItems.collectAsState()
        val isDiscoverLoading by viewModel.isDiscoverLoading.collectAsState()
        val isLoaded by viewModel.isLoaded.collectAsState()
        val searchResults by viewModel.searchResults.collectAsState()
        val selectedSeries by viewModel.selectedSeries.collectAsState()
        val seriesEpisodes by viewModel.seriesEpisodes.collectAsState()
        val isEpisodesLoading by viewModel.isEpisodesLoading.collectAsState()
        val resolvedForPlayback by viewModel.resolvedForPlayback.collectAsState()

        var currentScreen by remember { mutableStateOf("home") }
        var searchQuery by remember { mutableStateOf("") }

        LaunchedEffect(isLoggedIn) {
            if (isLoggedIn && !isLoaded) {
                viewModel.loadHome()
            }
        }

        LaunchedEffect(currentScreen) {
            if (currentScreen == "discover") {
                viewModel.loadDiscoverContent()
            }
        }

        var playbackItem by remember { mutableStateOf<CatalogItem?>(null) }

        LaunchedEffect(resolvedForPlayback) {
            resolvedForPlayback?.let { item ->
                playbackItem = item
                viewModel.clearResolvedForPlayback()
            }
        }

        val desktopPlayer = remember { DesktopVideoPlayer() }

        Window(
            onCloseRequest = ::exitApplication,
            title = "WalacTV",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            DisposableEffect(Unit) {
                onDispose { desktopPlayer.release() }
            }

            val currentPlaybackItem = playbackItem
            val currentSelectedSeries = selectedSeries

            // Save progress every 30 seconds during playback
            LaunchedEffect(currentPlaybackItem, desktopPlayer.isPlaying.value) {
                if (currentPlaybackItem != null && desktopPlayer.isPlaying.value) {
                    while (true) {
                        kotlinx.coroutines.delay(30_000)
                        val pos = desktopPlayer.positionMs.value
                        val dur = desktopPlayer.durationMs.value
                        if (pos > 0 && dur > 0) {
                            val item = currentPlaybackItem
                            if (item != null) {
                                val contentId = item.providerId ?: item.stableId
                                val isComplete = dur > 0 && (pos.toFloat() / dur) > 0.95f
                                if (isComplete) {
                                    watchProgressRepo.deleteProgress(contentId)
                                } else {
                                    watchProgressRepo.saveProgress(
                                        contentId = contentId,
                                        contentType = item.kind.name.lowercase(),
                                        positionMs = pos,
                                        durationMs = dur,
                                        title = item.title,
                                        imageUrl = item.imageUrl,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Save on close
            DisposableEffect(currentPlaybackItem) {
                onDispose {
                    currentPlaybackItem?.let { item ->
                        val pos = desktopPlayer.positionMs.value
                        val dur = desktopPlayer.durationMs.value
                        if (pos > 0 && dur > 0) {
                            kotlinx.coroutines.runBlocking {
                                val contentId = item.providerId ?: item.stableId
                                val isComplete = dur > 0 && (pos.toFloat() / dur) > 0.95f
                                if (isComplete) {
                                    watchProgressRepo.deleteProgress(contentId)
                                } else {
                                    watchProgressRepo.saveProgress(
                                        contentId = contentId,
                                        contentType = item.kind.name.lowercase(),
                                        positionMs = pos,
                                        durationMs = dur,
                                        title = item.title,
                                        imageUrl = item.imageUrl,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            when {
                currentPlaybackItem != null -> {
                    val stream = currentPlaybackItem.streamOptions.firstOrNull()
                    PlaybackScreen(
                        player = desktopPlayer,
                        title = currentPlaybackItem.title,
                        subtitle = currentPlaybackItem.subtitle,
                        streamUrl = stream?.url ?: "",
                        headers = stream?.headers ?: emptyMap(),
                        onBack = {
                            desktopPlayer.stop()
                            playbackItem = null
                        },
                        videoContent = {
                            DesktopVideoSurface(
                                player = desktopPlayer,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                    )
                }
                currentSelectedSeries != null -> {
                    SeriesDetailScreen(
                        series = currentSelectedSeries,
                        episodes = seriesEpisodes,
                        isLoading = isEpisodesLoading,
                        onEpisodeClick = { episode ->
                            val stream = episode.streamOptions.firstOrNull()
                            if (stream != null) {
                                playbackItem = episode
                            }
                            viewModel.clearSeriesSelection()
                        },
                        onBack = { viewModel.clearSeriesSelection() },
                    )
                }
                else -> {
                    val handleCardClick: (CatalogItem) -> Unit = { item ->
                        if (item.kind == ContentKind.SERIES && item.seriesName != null) {
                            viewModel.selectSeries(item)
                        } else {
                            viewModel.fetchAndPlayContent(item)
                        }
                    }

                    val navItems = listOf(
                        NavItem(icon = "\uD83C\uDFE0", label = "Inicio", route = "home"),
                        NavItem(icon = "\uD83D\uDD0D", label = "Buscar", route = "search"),
                        NavItem(icon = "\uD83D\uDCFA", label = "Descubrir", route = "discover"),
                        NavItem(icon = "\u2699\uFE0F", label = "Ajustes", route = "settings"),
                    )
                    val navIndex = navItems.indexOfFirst { it.route == currentScreen }.coerceAtLeast(0)

                    App(
                        isLoggedIn = isLoggedIn,
                        isLoading = isLoading && !isLoaded,
                        errorMessage = errorMessage,
                        onLogin = { user, pass -> viewModel.signIn(user, pass) },
                        onLogout = { viewModel.signOut() },
                        homeContent = {
                            Row(modifier = Modifier.fillMaxSize()) {
                                AdaptiveNavigationRail(
                                    items = navItems,
                                    selectedIndex = navIndex,
                                    onItemSelected = { index ->
                                        currentScreen = navItems.getOrNull(index)?.route ?: "home"
                                    },
                                )
                                Box(modifier = Modifier.fillMaxSize()) {
                                    when (currentScreen) {
                                        "home" -> HomeScreen(
                                            sections = homeSections,
                                            continueWatching = continueWatching,
                                            selectedHero = selectedHero,
                                            isLoading = isLoading,
                                            onCardClick = handleCardClick,
                                            onHeroClick = handleCardClick,
                                        )
                                        "search" -> SearchScreen(
                                            query = searchQuery,
                                            results = if (searchQuery.isBlank()) emptyList() else searchResults,
                                            onQueryChange = {
                                                searchQuery = it
                                                viewModel.search(it)
                                            },
                                            onCardClick = handleCardClick,
                                        )
                                        "discover" -> DiscoverScreen(
                                            items = discoverItems,
                                            isLoading = isLoading,
                                            isDiscoverLoading = isDiscoverLoading,
                                            onCardClick = handleCardClick,
                                        )
                                        "settings" -> SettingsScreen(
                                            versionName = "1.21.0 (Desktop)",
                                            channelCount = searchableItems.size,
                                            contentCount = searchableItems.size,
                                            preferredLanguage = viewModel.preferredLanguage,
                                            onLanguageChange = { code -> preferencesManager.preferredLanguage = code },
                                            onSignOut = { viewModel.signOut() },
                                        )
                                        else -> HomeScreen(
                                            sections = homeSections,
                                            continueWatching = continueWatching,
                                            selectedHero = selectedHero,
                                            isLoading = isLoading,
                                            onCardClick = handleCardClick,
                                            onHeroClick = handleCardClick,
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
