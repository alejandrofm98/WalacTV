package com.example.walactv.local

import android.util.Log
import com.example.walactv.CatalogItem
import com.example.walactv.ContentKind
import com.example.walactv.IptvRepository
import com.example.walactv.uniqueMovies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PagedContentLoader"

class PagedContentLoader(
    private val contentCacheManager: ContentCacheManager,
    private val repository: IptvRepository,
    private val kind: ContentKind,
    private val pageSize: Int = 50,
    private val maxCacheSize: Int = 100
) {
    private val cache = mutableListOf<CatalogItem>()
    private val loadedPages = mutableSetOf<Int>()
    private var totalCount = 0
    private var lastCountry: String? = null
    private var lastGroup: String? = null
    private var isSearchMode = false
    private var isLoading = false

    fun getDisplayItems(): List<CatalogItem> = cache.toList()
    fun getTotalCount(): Int = if (isSearchMode) cache.size else totalCount
    fun isPageLoaded(page: Int): Boolean = loadedPages.contains(page)
    fun getPageSize(): Int = pageSize
    fun isCurrentlyLoading(): Boolean = isLoading

    suspend fun loadPage(page: Int, country: String?, group: String?) {
        if (loadedPages.contains(page)) return
        if (isLoading) return

        if (country != lastCountry || group != lastGroup) {
            cache.clear()
            loadedPages.clear()
            lastCountry = country
            lastGroup = group
            isSearchMode = false
        }

        isLoading = true
        try {
            val user = repository.currentUsername()
            val pass = repository.currentPassword()
            val items = when (kind) {
                ContentKind.CHANNEL -> {
                    val entities = withContext(Dispatchers.IO) {
                        contentCacheManager.getChannelsPaged(country, group, page, pageSize)
                    }
                    entities.map { it.toCatalogItem(user, pass) }
                }
                ContentKind.MOVIE -> {
                    val entities = withContext(Dispatchers.IO) {
                        contentCacheManager.getMoviesPaged(country, group, page, pageSize)
                    }
                    entities.map { it.toCatalogItem(user, pass) }.uniqueMovies()
                }
                ContentKind.SERIES -> {
                    val entities = withContext(Dispatchers.IO) {
                        contentCacheManager.getSeriesPaged(country, group, page, pageSize)
                    }
                    entities.map { it.toCatalogItem(user, pass) }
                }
                else -> emptyList()
            }

            val insertIndex = page * pageSize
            val clampedIndex = insertIndex.coerceAtMost(cache.size)
            cache.addAll(clampedIndex, items)

            // Evict oldest items if exceeding maxCacheSize, but NEVER remove from loadedPages
            // loadedPages tracks what has been loaded from DB; cache is just the display window
            while (cache.size > maxCacheSize) {
                cache.removeAt(0)
            }

            loadedPages.add(page)
            Log.d(TAG, "loadPage($kind, page=$page): loaded ${items.size} items, cache size=${cache.size}, loadedPages=$loadedPages")
        } finally {
            isLoading = false
        }
    }

    suspend fun loadSearch(query: String) {
        Log.d(TAG, "loadSearch: starting search for '$query' with kind=$kind")
        cache.clear()
        loadedPages.clear()
        isSearchMode = true
        isLoading = true
        try {
            val user = repository.currentUsername()
            val pass = repository.currentPassword()
            Log.d(TAG, "loadSearch: calling search for kind=$kind, query='$query'")
            val items = when (kind) {
                ContentKind.CHANNEL -> {
                    val entities = withContext(Dispatchers.IO) { contentCacheManager.searchChannels(query) }
                    Log.d(TAG, "loadSearch: channels search returned ${entities.size} entities")
                    entities.map { it.toCatalogItem(user, pass) }
                }
                ContentKind.MOVIE -> {
                    val entities = withContext(Dispatchers.IO) { contentCacheManager.searchMovies(query) }
                    Log.d(TAG, "loadSearch: movies search returned ${entities.size} entities")
                    entities.map { it.toCatalogItem(user, pass) }.uniqueMovies()
                }
                ContentKind.SERIES -> {
                    val entities = withContext(Dispatchers.IO) { contentCacheManager.searchSeries(query) }
                    Log.d(TAG, "loadSearch: series search returned ${entities.size} entities")
                    entities.map { it.toCatalogItem(user, pass) }
                }
                else -> emptyList()
            }

            cache.addAll(items)
            Log.d(TAG, "loadSearch($kind, query='$query'): found ${cache.size} results")
        } finally {
            isLoading = false
        }
    }

    suspend fun refreshTotalCount(country: String?, group: String?) {
        isSearchMode = false
        totalCount = withContext(Dispatchers.IO) {
            when (kind) {
                ContentKind.CHANNEL -> contentCacheManager.getChannelsTotalCount(country, group)
                ContentKind.MOVIE -> contentCacheManager.getMoviesTotalCount(country, group)
                ContentKind.SERIES -> contentCacheManager.getSeriesTotalCount(country, group)
                else -> 0
            }
        }
    }

    fun clear() {
        cache.clear()
        loadedPages.clear()
        totalCount = 0
        lastCountry = null
        lastGroup = null
        isSearchMode = false
        isLoading = false
    }
}
