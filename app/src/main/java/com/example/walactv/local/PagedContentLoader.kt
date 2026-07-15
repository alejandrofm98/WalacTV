package com.example.walactv.local

import android.util.Log
import com.example.walactv.CatalogItem
import com.example.walactv.ContentKind
import com.example.walactv.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PagedContentLoader"

class PagedContentLoader(
    private val contentCacheManager: ContentCacheManager,
    private val repository: IptvRepository,
    private val kind: ContentKind,
    private val pageSize: Int = 50
) {
    private val cache = mutableListOf<CatalogItem>()
    private val loadedPages = mutableSetOf<Int>()
    private var totalCount = 0
    private var lastCountry: String? = null
    private var lastGenre: String? = null
    private var isSearchMode = false
    private var isLoading = false

    fun getDisplayItems(): List<CatalogItem> = cache.toList()
    fun getTotalCount(): Int = if (isSearchMode) cache.size else totalCount
    fun isPageLoaded(page: Int): Boolean = loadedPages.contains(page)
    fun isCurrentlyLoading(): Boolean = isLoading

    suspend fun loadPage(page: Int, country: String?, group: String? = null, genre: String? = null) {
        // Check filter change FIRST: if the country/genre changed, the cached pages are
        // stale, so clear loadedPages before the "already loaded" short-circuit below.
        if (country != lastCountry || genre != lastGenre) {
            Log.d(TAG, "loadPage($kind, page=$page): filter changed (country: $lastCountry→$country, genre: $lastGenre→$genre), clearing cache")
            cache.clear()
            loadedPages.clear()
            lastCountry = country
            lastGenre = genre
            isSearchMode = false
        }

        if (loadedPages.contains(page)) {
            Log.d(TAG, "loadPage($kind, page=$page): already loaded, skipping")
            return
        }
        if (isLoading) {
            Log.w(TAG, "loadPage($kind, page=$page): another load in progress, skipping")
            return
        }

        isLoading = true
        try {
            Log.d(TAG, "loadPage($kind, page=$page): starting load, country=$country, group=$group, genre=$genre, cache.size=${cache.size}")
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
                    val result = repository.loadCatalogPage(kind, page + 1, country, group, genre = genre)
                    totalCount = result.total
                    result.items
                }
                ContentKind.SERIES -> {
                    val result = repository.loadCatalogPage(kind, page + 1, country, group, genre = genre)
                    totalCount = result.total
                    result.items
                }
                else -> emptyList()
            }

            Log.d(TAG, "loadPage($kind, page=$page): fetched ${items.size} items, totalCount=$totalCount")

            // Append items at the end of cache (sequential loading guarantee)
            val insertIndex = cache.size
            cache.addAll(insertIndex, items)

            // Deduplicate by stableId to prevent server-side duplicates
            val beforeDedup = cache.size
            val seen = mutableSetOf<String>()
            val iterator = cache.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (!seen.add(item.stableId)) {
                    iterator.remove()
                }
            }
            val removed = beforeDedup - cache.size
            if (removed > 0) {
                Log.w(TAG, "loadPage($kind, page=$page): removed $removed duplicate items")
            }

            if (kind == ContentKind.CHANNEL) {
                val beforeMerge = cache.size
                val merged = com.example.walactv.mergeChannelVariants(cache.toList())
                cache.clear()
                cache.addAll(merged)
                Log.d(TAG, "loadPage($kind, page=$page): merged channels: $beforeMerge → ${cache.size}")
            }

            loadedPages.add(page)
            Log.d(TAG, "loadPage($kind, page=$page): cache.size=${cache.size}, loadedPages=$loadedPages")
        } catch (e: Exception) {
            Log.e(TAG, "loadPage($kind, page=$page): failed", e)
            throw e
        } finally {
            isLoading = false
        }
    }

    suspend fun loadSearch(query: String, country: String? = null, group: String? = null, genre: String? = null) {
        Log.d(TAG, "loadSearch: starting search for '$query' with kind=$kind, country=$country, group=$group, genre=$genre")
        cache.clear()
        loadedPages.clear()
        isSearchMode = true
        isLoading = true
        try {
            val user = repository.currentUsername()
            val pass = repository.currentPassword()
            Log.d(TAG, "loadSearch: calling search for kind=$kind, query='$query', country=$country, group=$group, genre=$genre")
            val items = when (kind) {
                ContentKind.CHANNEL -> {
                    val entities = withContext(Dispatchers.IO) { contentCacheManager.searchChannels(query, country, group) }
                    Log.d(TAG, "loadSearch: channels search returned ${entities.size} entities")
                    entities.map { it.toCatalogItem(user, pass) }
                }
                ContentKind.MOVIE -> {
                    val result = repository.loadCatalogPage(kind, 1, country, group, query, genre)
                    totalCount = result.total
                    Log.d(TAG, "loadSearch: movies search returned ${result.items.size} items")
                    result.items
                }
                ContentKind.SERIES -> {
                    val result = repository.loadCatalogPage(kind, 1, country, group, query, genre)
                    totalCount = result.total
                    Log.d(TAG, "loadSearch: series search returned ${result.items.size} items")
                    result.items
                }
                else -> emptyList()
            }

            cache.addAll(items)
            Log.d(TAG, "loadSearch($kind, query='$query', country=$country, group=$group): found ${cache.size} results")
        } catch (e: Exception) {
            Log.e(TAG, "loadSearch($kind, query='$query'): failed", e)
            throw e
        } finally {
            isLoading = false
        }
    }

    suspend fun refreshTotalCount(country: String?, group: String?) {
        isSearchMode = false
        totalCount = withContext(Dispatchers.IO) {
            when (kind) {
                ContentKind.CHANNEL -> contentCacheManager.getChannelsTotalCount(country, group)
                ContentKind.MOVIE,
                ContentKind.SERIES,
                -> totalCount
                else -> 0
            }
        }
    }

    suspend fun loadUntilFound(targetStableId: String, country: String?, group: String?): Int {
        clear()
        refreshTotalCount(country, group)
        if (totalCount <= 0) return -1

        var page = 0
        val maxPages = (totalCount + pageSize - 1) / pageSize
        while (page < maxPages) {
            if (isCurrentlyLoading()) {
                kotlinx.coroutines.delay(50)
                continue
            }
            loadPage(page, country, group)
            val index = cache.indexOfFirst { it.stableId == targetStableId }
            if (index >= 0) {
                Log.d(TAG, "loadUntilFound($targetStableId): found at index $index on page $page")
                return index
            }
            if (cache.isEmpty()) break
            page++
        }
        Log.d(TAG, "loadUntilFound($targetStableId): not found within $maxPages pages")
        return -1
    }

    fun clear() {
        Log.d(TAG, "clear($kind): clearing loader, cache.size=${cache.size}, loadedPages=$loadedPages")
        cache.clear()
        loadedPages.clear()
        totalCount = 0
        lastCountry = null
        lastGenre = null
        isSearchMode = false
        isLoading = false
    }
}
