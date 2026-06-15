package com.example.walactv.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.walactv.shared.data.ChannelStateStore
import com.example.walactv.shared.data.ContentCacheManager
import com.example.walactv.shared.data.CredentialStore
import com.example.walactv.shared.data.IptvRepository
import com.example.walactv.shared.data.PreferencesManager
import com.example.walactv.shared.data.WatchProgressRepository
import com.example.walactv.shared.domain.CatalogItem
import com.example.walactv.shared.domain.FormFactor
import com.example.walactv.shared.domain.FormFactorDetector
import com.example.walactv.shared.domain.HomeViewModel
import com.example.walactv.shared.ui.App
import com.example.walactv.shared.ui.components.AdaptiveNavigationRail
import com.example.walactv.shared.ui.components.NavItem
import com.example.walactv.shared.ui.screens.SearchScreen
import com.example.walactv.shared.ui.screens.SeriesDetailScreen
import com.example.walactv.shared.ui.screens.DiscoverScreen
import com.example.walactv.shared.ui.screens.SettingsScreen
import com.example.walactv.shared.ui.screens.MobileHomeScreen
import com.example.walactv.shared.domain.ContentKind
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val credentialStore: CredentialStore by inject()
    private val preferencesManager: PreferencesManager by inject()
    private val repository: IptvRepository by inject()
    private val watchProgressRepo: WatchProgressRepository by inject()
    private val channelStateStore: ChannelStateStore by inject()
    private val contentCacheManager: ContentCacheManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FormFactorDetector.current = FormFactor.MOBILE

        val scope = lifecycleScope
        val viewModel = HomeViewModel(
            repository = repository,
            watchProgressRepo = watchProgressRepo,
            credentialStore = credentialStore,
            preferencesManager = preferencesManager,
            channelStateStore = channelStateStore,
            contentCacheManager = contentCacheManager,
            scope = scope,
        )

        setContent {
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
            val isSearching by viewModel.isSearching.collectAsState()
            val selectedSeries by viewModel.selectedSeries.collectAsState()
            val seriesEpisodes by viewModel.seriesEpisodes.collectAsState()
            val isEpisodesLoading by viewModel.isEpisodesLoading.collectAsState()
            val resolvedForPlayback by viewModel.resolvedForPlayback.collectAsState()

            var currentScreen by remember { mutableStateOf("home") }
            var searchQuery by remember { mutableStateOf("") }

            // Playback navigation state
            val playbackScope = rememberCoroutineScope()
            var playbackItem by remember { mutableStateOf<CatalogItem?>(null) }
            var playbackUrl by remember { mutableStateOf<String?>(null) }
            var playbackTitle by remember { mutableStateOf("") }
            var playbackSubtitle by remember { mutableStateOf("") }

            // Consume resolved content from fetchAndPlayContent
            LaunchedEffect(resolvedForPlayback) {
                resolvedForPlayback?.let { item ->
                    val stream = item.streamOptions.firstOrNull()
                    if (stream != null) {
                        playbackUrl = stream.url
                        playbackTitle = item.title
                        playbackSubtitle = item.subtitle
                        playbackItem = item
                    }
                    viewModel.clearResolvedForPlayback()
                }
            }

            val navItems = remember {
                listOf(
                    NavItem(icon = "\uD83C\uDFE0", label = "Home", route = "home"),
                    NavItem(icon = "\uD83D\uDD0D", label = "Search", route = "search"),
                    NavItem(icon = "\uD83E\uDDED", label = "Discover", route = "discover"),
                    NavItem(icon = "\u2699\uFE0F", label = "Settings", route = "settings"),
                )
            }
            val selectedIndex = navItems.indexOfFirst { it.route == currentScreen }.coerceAtLeast(0)

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

            val handleCardClick: (CatalogItem) -> Unit = { item ->
                if (item.kind == ContentKind.SERIES && item.seriesName != null) {
                    viewModel.selectSeries(item)
                } else {
                    viewModel.fetchAndPlayContent(item)
                }
            }

            if (playbackUrl != null) {
                val currentItem = playbackItem
                MobilePlaybackScreen(
                    streamUrl = playbackUrl!!,
                    title = playbackTitle,
                    subtitle = playbackSubtitle,
                    contentId = currentItem?.providerId ?: currentItem?.stableId,
                    contentType = currentItem?.kind?.name,
                    onBack = {
                        playbackUrl = null
                        playbackTitle = ""
                        playbackSubtitle = ""
                        playbackItem = null
                    },
                    onSaveProgress = { positionMs, durationMs ->
                        val item = currentItem
                        if (item != null) {
                            playbackScope.launch {
                                val contentId = item.providerId ?: item.stableId
                                val isComplete = durationMs > 0 && (positionMs.toFloat() / durationMs) > 0.95f
                                if (isComplete) {
                                    watchProgressRepo.deleteProgress(contentId)
                                } else {
                                    val user = credentialStore.username()
                                    val pass = credentialStore.password()
                                    if (user.isNotBlank() && pass.isNotBlank()) {
                                        watchProgressRepo.saveProgress(
                                            contentId = contentId,
                                            contentType = item.kind.name.lowercase(),
                                            positionMs = positionMs,
                                            durationMs = durationMs,
                                            title = item.title,
                                            imageUrl = item.imageUrl,
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            } else if (selectedSeries != null) {
                SeriesDetailScreen(
                    series = selectedSeries!!,
                    episodes = seriesEpisodes,
                    isLoading = isEpisodesLoading,
                    onEpisodeClick = { episode ->
                        val stream = episode.streamOptions.firstOrNull()
                        if (stream != null) {
                            playbackUrl = stream.url
                            playbackTitle = episode.title
                            playbackSubtitle = episode.subtitle
                        }
                        viewModel.clearSeriesSelection()
                    },
                    onBack = { viewModel.clearSeriesSelection() },
                )
            } else {
                App(
                    isLoggedIn = isLoggedIn,
                    isLoading = isLoading && !isLoaded,
                    errorMessage = errorMessage,
                    onLogin = { user, pass -> viewModel.signIn(user, pass) },
                    onLogout = { viewModel.signOut() },
                    homeContent = {
                        Scaffold(
                            bottomBar = {
                                AdaptiveNavigationRail(
                                    items = navItems,
                                    selectedIndex = selectedIndex,
                                    onItemSelected = { index ->
                                        currentScreen = navItems[index].route
                                    },
                                )
                            },
                        ) { paddingValues ->
                            Box(modifier = Modifier.padding(paddingValues)) {
                                when (currentScreen) {
                                    "home" -> MobileHomeScreen(
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
                                        versionName = "1.21.0",
                                        channelCount = searchableItems.size,
                                        contentCount = searchableItems.size,
                                        preferredLanguage = viewModel.preferredLanguage,
                                        onLanguageChange = { code -> preferencesManager.preferredLanguage = code },
                                        onSignOut = { viewModel.signOut() },
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
