package com.example.walactv.shared.domain

import com.example.walactv.shared.data.ChannelStateStore
import com.example.walactv.shared.data.ContentCacheManager
import com.example.walactv.shared.data.CredentialStore
import com.example.walactv.shared.data.IptvRepository
import com.example.walactv.shared.data.PreferencesManager
import com.example.walactv.shared.data.WatchProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: IptvRepository,
    private val watchProgressRepo: WatchProgressRepository,
    private val credentialStore: CredentialStore,
    private val preferencesManager: PreferencesManager,
    private val channelStateStore: ChannelStateStore,
    private val contentCacheManager: ContentCacheManager? = null,
    private val scope: CoroutineScope,
) {
    // Auth state
    private val _isLoggedIn = MutableStateFlow(credentialStore.hasCredentials())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Catalog state
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

    private val _searchResults = MutableStateFlow<List<CatalogItem>>(emptyList())
    val searchResults: StateFlow<List<CatalogItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _seriesEpisodes = MutableStateFlow<List<CatalogItem>>(emptyList())
    val seriesEpisodes: StateFlow<List<CatalogItem>> = _seriesEpisodes.asStateFlow()

    private val _selectedSeries = MutableStateFlow<CatalogItem?>(null)
    val selectedSeries: StateFlow<CatalogItem?> = _selectedSeries.asStateFlow()

    private val _isEpisodesLoading = MutableStateFlow(false)
    val isEpisodesLoading: StateFlow<Boolean> = _isEpisodesLoading.asStateFlow()

    private val _discoverItems = MutableStateFlow<List<CatalogItem>>(emptyList())
    val discoverItems: StateFlow<List<CatalogItem>> = _discoverItems.asStateFlow()

    private val _isDiscoverLoading = MutableStateFlow(false)
    val isDiscoverLoading: StateFlow<Boolean> = _isDiscoverLoading.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _selectedHero = MutableStateFlow<CatalogItem?>(null)
    val selectedHero: StateFlow<CatalogItem?> = _selectedHero.asStateFlow()

    // Settings
    val preferredLanguage: String get() = preferencesManager.getPreferredLanguageOrDefault()

    fun signIn(username: String, password: String) {
        scope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                repository.signIn(username, password)
                _isLoggedIn.value = true
                loadHome(forceRefresh = true)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Error al iniciar sesion"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        repository.signOut()
        _isLoggedIn.value = false
        _homeCatalog.value = null
        _homeSections.value = emptyList()
        _continueWatchingSection.value = null
        _continueWatchingEntries.value = emptyMap()
        _searchableItems.value = emptyList()
        _discoverItems.value = emptyList()
        _searchResults.value = emptyList()
        _isSearching.value = false
        _seriesEpisodes.value = emptyList()
        _selectedSeries.value = null
        _isEpisodesLoading.value = false
        _isLoaded.value = false
        _selectedHero.value = null
    }

    fun loadHome(forceRefresh: Boolean = false) {
        scope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val catalog = repository.loadHomeCatalog(forceRefresh)
                _homeCatalog.value = catalog
                _homeSections.value = catalog.sections
                _searchableItems.value = catalog.searchableItems
                _isLoaded.value = true
                if (_selectedHero.value == null) {
                    _selectedHero.value = catalog.searchableItems.firstOrNull()
                }
                loadContinueWatching()
                syncContentToCache(catalog)
            } catch (e: Exception) {
                if (!_isLoaded.value) {
                    _errorMessage.value = e.message ?: "Error al cargar contenido"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun syncContentToCache(catalog: HomeCatalog) {
        val cache = contentCacheManager ?: return
        scope.launch(Dispatchers.Default) {
            try {
                val movies = catalog.searchableItems.filter { it.kind == ContentKind.MOVIE }
                val series = catalog.searchableItems.filter { it.kind == ContentKind.SERIES }
                val channels = catalog.searchableItems.filter { it.kind == ContentKind.CHANNEL }
                if (movies.isNotEmpty() || series.isNotEmpty() || channels.isNotEmpty()) {
                    cache.syncAllContent(movies, series, channels)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadContinueWatching() {
        scope.launch {
            try {
                val result = watchProgressRepo.getContinueWatching()
                val items = result.getOrDefault(emptyList())
                if (items.isNotEmpty()) {
                    val entryMap = mutableMapOf<String, WatchProgressItem>()
                    items.forEach { wp ->
                        entryMap[wp.contentId] = wp
                        val prefix = if (wp.contentType == "series") "series" else "movie"
                        entryMap["$prefix:${wp.contentId}"] = wp
                    }
                    _continueWatchingEntries.value = entryMap

                    val catalogItems = items.map { wp ->
                        val kind = if (wp.contentType == "series") ContentKind.SERIES else ContentKind.MOVIE
                        val subtitle = if (wp.contentType == "series") {
                            buildEpisodeLabel(wp.seasonNumber, wp.episodeNumber)
                        } else ""
                        val title = wp.normalizedTitle.ifBlank { wp.title }
                        CatalogItem(
                            stableId = "cw_${wp.contentType}:${wp.contentId}",
                            providerId = wp.contentId,
                            title = title,
                            subtitle = subtitle,
                            description = wp.overview ?: "",
                            imageUrl = wp.imageUrl.ifBlank {
                                buildTmdbImageUrl(wp.posterPath, "w500").orEmpty()
                            },
                            kind = kind,
                            group = "Continuar viendo",
                            badgeText = "",
                            streamOptions = emptyList(),
                        )
                    }
                    _continueWatchingSection.value = BrowseSection("Continuar viendo", catalogItems)
                } else {
                    _continueWatchingSection.value = null
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadSeriesEpisodes(seriesName: String) {
        scope.launch {
            _isEpisodesLoading.value = true
            try {
                val episodes = repository.loadSeriesEpisodes(seriesName)
                _seriesEpisodes.value = episodes
            } catch (_: Exception) {
                _seriesEpisodes.value = emptyList()
            } finally {
                _isEpisodesLoading.value = false
            }
        }
    }

    fun refreshEvents() {
        scope.launch {
            try {
                val catalog = repository.loadHomeCatalog(forceRefresh = true)
                _homeCatalog.value = catalog
                _homeSections.value = catalog.sections
                _searchableItems.value = catalog.searchableItems
            } catch (_: Exception) {
            }
        }
    }

    fun loadDiscoverContent() {
        scope.launch {
            _isDiscoverLoading.value = true
            try {
                val movies = repository.loadCatalogPage(ContentKind.MOVIE, page = 1)
                val series = repository.loadCatalogPage(ContentKind.SERIES, page = 1)
                _discoverItems.value = (movies.items + series.items).distinctBy(CatalogItem::stableId)
            } catch (_: Exception) {
                _discoverItems.value = _searchableItems.value.filter {
                    it.kind == ContentKind.MOVIE || it.kind == ContentKind.SERIES
                }
            } finally {
                _isDiscoverLoading.value = false
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        scope.launch {
            _isSearching.value = true
            try {
                val results = repository.search(query)
                _searchResults.value = results
            } catch (_: Exception) {
                _searchResults.value = _searchableItems.value.filter {
                    it.title.contains(query, ignoreCase = true)
                }
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    fun selectSeries(series: CatalogItem) {
        _selectedSeries.value = series
        val name = series.seriesName ?: series.title
        loadSeriesEpisodes(name)
    }

    fun clearSeriesSelection() {
        _selectedSeries.value = null
        _seriesEpisodes.value = emptyList()
    }

    fun selectHero(item: CatalogItem?) {
        _selectedHero.value = item
    }

    private val _fetchingContentId = MutableStateFlow<String?>(null)
    val fetchingContentId: StateFlow<String?> = _fetchingContentId.asStateFlow()

    private val _resolvedForPlayback = MutableStateFlow<CatalogItem?>(null)
    val resolvedForPlayback: StateFlow<CatalogItem?> = _resolvedForPlayback.asStateFlow()

    fun fetchAndPlayContent(item: CatalogItem) {
        if (item.streamOptions.isNotEmpty()) {
            _resolvedForPlayback.value = item
            return
        }
        val id = item.providerId ?: item.stableId.substringAfterLast(":")
        if (id.isBlank()) return

        scope.launch {
            _fetchingContentId.value = item.stableId
            try {
                val resolved = repository.fetchContentItem(item.kind, id)
                if (resolved != null && resolved.streamOptions.isNotEmpty()) {
                    _resolvedForPlayback.value = resolved
                }
            } catch (_: Exception) {
            } finally {
                _fetchingContentId.value = null
            }
        }
    }

    fun clearResolvedForPlayback() {
        _resolvedForPlayback.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun buildEpisodeLabel(season: Int?, episode: Int?): String {
        if (season == null && episode == null) return ""
        return "T${season ?: 0} E${episode ?: 0}"
    }
}
