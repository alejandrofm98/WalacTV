package com.example.walactv.data.remote.repository

import android.content.Context
import android.util.Log
import com.example.walactv.data.remote.api.AuthInterceptor
import com.example.walactv.data.remote.api.IptvApiService
import com.example.walactv.data.remote.api.dto.CatalogItemDto
import com.example.walactv.data.remote.api.dto.CalendarEventDto
import com.example.walactv.data.remote.api.dto.CanalResueltoDto
import com.example.walactv.data.remote.api.dto.FilterOptionDto
import com.example.walactv.data.remote.api.dto.FilterOptionsResponse
import com.example.walactv.data.remote.api.dto.HomeCatalogResponse
import com.example.walactv.data.remote.api.dto.ReplayDto
import com.example.walactv.data.remote.api.dto.ReplayListResponse
import com.example.walactv.data.remote.api.dto.PlaybackPreferenceDto
import com.example.walactv.data.remote.api.dto.PlaybackPreferenceUpdateBody
import com.example.walactv.data.remote.api.dto.SearchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import com.example.walactv.BuildConfig
import com.example.walactv.data.model.BrowseSection
import com.example.walactv.data.model.CatalogFilters
import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.model.HomeCatalog
import com.example.walactv.data.model.RemoteCatalogPage
import com.example.walactv.data.model.StreamOption
import com.example.walactv.data.playlist.M3uCatalogStore
import com.example.walactv.data.preferences.CredentialStore
import com.example.walactv.data.preferences.PreferencesManager
import com.example.walactv.data.util.isTmdbImagePath
import com.example.walactv.data.util.normalizeRemoteImageUrl
import com.example.walactv.WalacApp
import com.example.walactv.data.model.cleanQualityLabels
import com.example.walactv.data.model.parseNormalizedMetadata
import com.example.walactv.ui.compose.buildTmdbImageUrl

internal val EVENT_QUALITY_ORDER = mapOf(
    "UHD" to 7, "4K" to 6, "FHD" to 5, "HD" to 4, "SD" to 3,
    "HEVC" to 3, "H265" to 3, "HQ" to 2, "LQ" to 1,
)

internal fun mergeChannelVariants(items: List<CatalogItem>): List<CatalogItem> {
    val (channels, nonChannels) = items.partition { it.kind == ContentKind.CHANNEL }
    Log.d("mergeChannels", "channels=${channels.size} nonChannels=${nonChannels.size}")
    channels.forEachIndexed { i, c ->
        Log.d("mergeChannels", "  channel[$i] title='${c.title}' group='${c.group}' stableId='${c.stableId}' streamOptions=${c.streamOptions.size}")
    }
    val merged = channels.groupBy { it.title to it.group }
        .map { (key, groupItems) ->
            Log.d("mergeChannels", "  group='$key' count=${groupItems.size}")
            val representative = groupItems.first()
            val mergedOptions = groupItems
                .flatMap { it.streamOptions }
                .distinctBy { it.url.takeIf { u -> u.isNotBlank() } ?: it.label }
                .sortedWith(
                    compareByDescending<StreamOption> { EVENT_QUALITY_ORDER[it.quality?.uppercase()?.trim()] ?: 0 }
                        .thenBy { it.label }
                )
            representative.copy(streamOptions = mergedOptions)
        }
    Log.d("mergeChannels", "result=${nonChannels.size + merged.size} (${nonChannels.size} non + ${merged.size} merged)")
    return nonChannels + merged
}

@Singleton
class IptvRepository @Inject constructor(context: Context) {

    private val appContext = context.applicationContext
    private val m3uCatalogStore = M3uCatalogStore(appContext)

    private val appComponent = (appContext as WalacApp).appComponent
    private val apiService: IptvApiService = appComponent.apiService
    private val authInterceptor: AuthInterceptor = appComponent.authInterceptor

    // ── Caches ────────────────────────────────────────────────────────────────

    private val filterCache = mutableMapOf<String, CatalogFilters>()

    @Volatile private var memoryHomeCatalog: HomeCatalog? = null

    // ── Credenciales / sesion ─────────────────────────────────────────────────

    fun hasStoredCredentials(): Boolean = CredentialStore.hasCredentials()
    fun currentUsername(): String = CredentialStore.username()
    fun currentPassword(): String = CredentialStore.password()

    suspend fun signIn(username: String, password: String) {
        val user = username.trim()
        val pass = password.trim()
        require(user.isNotBlank() && pass.isNotBlank()) { "Introduce usuario y contrasena" }
        require(isBaseUrlConfigured()) { "Falta configurar walactv.iptvBaseUrl en local.properties" }

        Log.d(TAG, "Intentando login en ${BuildConfig.IPTV_BASE_URL} con usuario ${maskUsername(user)}")
        try {
            val response = apiService.login(user, pass)
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code()}: ${response.errorBody()?.string()}")
            }
            val token = response.body()?.access_token
                ?: response.body()?.token
                ?: response.body()?.access
                ?: throw IllegalStateException("Respuesta de login sin access_token")
            authInterceptor.token = token
            CredentialStore.save(user, pass)
            clearAllCaches()
            m3uCatalogStore.clearAllCache()
            Log.d(TAG, "Login correcto para ${maskUsername(user)}")
        } catch (e: Exception) {
            Log.e(TAG, "Fallo en login para ${maskUsername(user)}", e)
            throw IllegalStateException(buildLoginErrorMessage(e), e)
        }
    }

    fun signOut() {
        authInterceptor.token = null
        CredentialStore.clear()
        clearAllCaches()
        m3uCatalogStore.clearAllCache()
    }

    fun clearHomeMemoryCache() = clearAllCaches()

    fun updateHomeEventsCache(eventSections: List<BrowseSection>) {
        val current = memoryHomeCatalog ?: return
        val freshEventIds = eventSections.flatMap(BrowseSection::items).mapTo(mutableSetOf(), CatalogItem::stableId)
        val nonEventSections = current.sections.filterNot { section ->
            section.items.any { it.kind == ContentKind.EVENT || it.stableId in freshEventIds }
        }
        memoryHomeCatalog = current.copy(
            sections = eventSections + nonEventSections,
            searchableItems = (eventSections.flatMap(BrowseSection::items) + current.searchableItems.filterNot { it.kind == ContentKind.EVENT })
                .distinctBy(CatalogItem::stableId),
        )
    }

    suspend fun refreshPlaylistNow(): Long {
        m3uCatalogStore.refreshNow()
        memoryHomeCatalog = null
        return m3uCatalogStore.getLastUpdatedMillis()
    }

    fun getLastPlaylistUpdateMillis(): Long = m3uCatalogStore.getLastUpdatedMillis()

    private fun clearAllCaches() {
        memoryHomeCatalog = null
        filterCache.clear()
    }

    // ── Home catalog ──────────────────────────────────────────────────────────

    suspend fun loadHomeCatalog(forceRefresh: Boolean = false): HomeCatalog =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) memoryHomeCatalog?.let { return@withContext it }

            coroutineScope {
                val eventsDeferred = async {
                    safeSectionLoad("eventos") { fetchEventSections() }
                }
                val remoteDeferred = async { fetchRemoteHomeCatalog() }

                val eventSections = eventsDeferred.await()
                    .map { s -> s.copy(items = resolveStreamTemplates(s.items)) }
                val remote = remoteDeferred.await()

                HomeCatalog(
                    sections = eventSections + remote.sections,
                    searchableItems = (eventSections.flatMap(BrowseSection::items) + remote.searchableItems)
                        .distinctBy(CatalogItem::stableId),
                    favoriteItems = remote.favoriteItems,
                ).also { if (remote.sections.isNotEmpty()) memoryHomeCatalog = it }
            }
        }

    suspend fun loadEventsOnly(): HomeCatalog = withContext(Dispatchers.IO) {
        val sections = safeSectionLoad("eventos") { fetchEventSections() }
        val resolved = sections.map { s -> s.copy(items = resolveStreamTemplates(s.items)) }
        HomeCatalog(
            sections = resolved,
            searchableItems = resolved.flatMap(BrowseSection::items).distinctBy(CatalogItem::stableId),
            favoriteItems = null,
        )
    }

    suspend fun updateChannelFavorite(item: CatalogItem, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        if (item.kind != ContentKind.CHANNEL) {
            Log.d(TAG, "FAV_API: SKIP kind=${item.kind} id=${item.stableId}")
            return@withContext
        }

        val favoriteId = item.providerId
            ?.takeIf { it.isNotBlank() }
            ?: item.stableId.substringAfter("channel:", item.stableId)
        require(favoriteId.isNotBlank()) { "No se puede actualizar el favorito sin providerId" }

        Log.d(TAG, "FAV_API: START id=${item.stableId} providerId=$favoriteId favorite=$isFavorite")
        try {
            if (isFavorite) {
                apiService.addFavorite(favoriteId)
            } else {
                apiService.removeFavorite(favoriteId)
            }
            Log.d(TAG, "FAV_API: OK id=${item.stableId} favorite=$isFavorite")
        } catch (e: Exception) {
            Log.e(TAG, "FAV_API: FAIL id=${item.stableId} favorite=$isFavorite", e)
            throw e
        }
    }

    // ── Filtros ───────────────────────────────────────────────────────────────

    suspend fun loadCatalogFilters(
        kind: ContentKind,
        country: String? = null,
    ): CatalogFilters = withContext(Dispatchers.IO) {
        if (kind == ContentKind.EVENT || kind == ContentKind.UFC) return@withContext CatalogFilters()

        val cacheKey = "${kind.name}|${country.orEmpty()}"
        filterCache[cacheKey]?.let { return@withContext it }

        val contentType = kind.toApiType()

        val countries: List<FilterOptionDto>
        val countriesForGroupQuery: String

        if (country == null) {
            val response = apiService.getCountries(contentType)
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}")
            val payload = response.body() ?: FilterOptionsResponse()
            countries = payload.countries.map { FilterOptionDto(value = it.value, label = it.label) }
            countriesForGroupQuery = countries.joinToString(",", transform = FilterOptionDto::value)
        } else {
            val baseKey = "${kind.name}|"
            countries = filterCache[baseKey]?.countries ?: run {
                val response = apiService.getCountries(contentType)
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}")
                val payload = response.body() ?: FilterOptionsResponse()
                payload.countries.map { FilterOptionDto(value = it.value, label = it.label) }
            }
            countriesForGroupQuery = country
        }

        val groupsResponse = if (countriesForGroupQuery.isNotBlank()) {
            apiService.getGroups(contentType, countriesForGroupQuery)
        } else {
            apiService.getGroups(contentType)
        }
        if (!groupsResponse.isSuccessful) throw IllegalStateException("HTTP ${groupsResponse.code()}")
        val groupsPayload = groupsResponse.body() ?: FilterOptionsResponse()

        val groups = if (kind == ContentKind.CHANNEL) {
            listOf(FilterOptionDto(FAVORITES_FILTER_VALUE, FAVORITES_FILTER_LABEL)) +
                groupsPayload.groups.map { FilterOptionDto(value = it.value, label = it.label) }
        } else {
            groupsPayload.groups.map { FilterOptionDto(value = it.value, label = it.label) }
        }

        val genres = if (kind == ContentKind.MOVIE || kind == ContentKind.SERIES) {
            val genresResponse = apiService.getGenres(contentType)
            if (genresResponse.isSuccessful) {
                (genresResponse.body()?.genres ?: emptyList()).map { FilterOptionDto(value = it, label = it) }
            } else emptyList()
        } else emptyList()

        CatalogFilters(countries = countries, groups = groups, genres = genres)
            .also { filterCache[cacheKey] = it }
    }

    // ── Contenido paginado ────────────────────────────────────────────────────

    suspend fun loadCatalogPage(
        kind: ContentKind,
        page: Int,
        country: String? = null,
        group: String? = null,
        search: String? = null,
        genre: String? = null,
    ): RemoteCatalogPage = withContext(Dispatchers.IO) {
        if (kind == ContentKind.EVENT) {
            return@withContext RemoteCatalogPage(emptyList(), 0, page, 0, 0, false, false)
        }

        if (kind == ContentKind.CHANNEL && group == FAVORITES_FILTER_VALUE) {
            val response = apiService.getFavorites()
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}")
            val items = response.body().orEmpty().map { it.toCatalogItem(ContentKind.CHANNEL) }
            val resolved = resolveStreamTemplates(items).distinctBy(CatalogItem::stableId)
            return@withContext RemoteCatalogPage(
                items = resolved,
                total = resolved.size,
                page = 1,
                pageSize = resolved.size,
                pages = 1,
                hasNext = false,
                hasPrev = false,
            )
        }

        val response = apiService.getCatalogPage(
            contentType = kind.toApiType(),
            country = country?.takeIf { it.isNotBlank() },
            group = group?.takeIf { it.isNotBlank() },
            search = search?.takeIf { it.isNotBlank() },
            genre = genre?.takeIf { it.isNotBlank() },
            page = page,
            pageSize = PAGE_SIZE,
        )
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}")
        val payload = response.body() ?: throw IllegalStateException("Empty response body")
        val items = payload.items.map { it.toCatalogItem(kind) }
        val resolved = resolveStreamTemplates(items).distinctBy(CatalogItem::stableId)
        RemoteCatalogPage(
            items = resolved,
            total = payload.total,
            page = payload.page,
            pageSize = payload.pageSize,
            pages = payload.pages,
            hasNext = payload.hasNext,
            hasPrev = payload.hasPrev,
        )
    }

    // ── UFC replays ───────────────────────────────────────────────────────────

    suspend fun loadUfcEvents(page: Int = 1, pageSize: Int = 24): Pair<List<CatalogItem>, Int> = withContext(Dispatchers.IO) {
        val token = runCatching { getAccessToken() }.getOrNull()
        try {
            val response = apiService.getReplays(eventType = "UFC", page = page, pageSize = pageSize)
            if (!response.isSuccessful) {
                val errBody = response.errorBody()?.string()?.take(200)
                Log.e(TAG, "UFC replays failed: http=${response.code()} errBody=$errBody")
                return@withContext Pair(emptyList(), 0)
            }
            val body = response.body() ?: run {
                Log.e(TAG, "UFC replays failed: null body")
                return@withContext Pair(emptyList(), 0)
            }
            val items = body.items.mapNotNull { dto -> mapReplayToCatalogItem(dto, token) }
            val total = body.total ?: items.size
            Log.d(TAG, "UFC replays: loaded ${items.size} items, total=$total")
            if (items.isNotEmpty()) {
                val first = body.items.first()
                Log.d(TAG, "UFC replays: first item eventType=${first.eventType} slug=${first.slug}")
            }
            Pair(items, total)
        } catch (e: Exception) {
            Log.e(TAG, "UFC replays failed with exception", e)
            Pair(emptyList(), 0)
        }
    }

    private fun mapReplayToCatalogItem(dto: ReplayDto, token: String?): CatalogItem? {
        val title = dto.eventName?.takeIf { it.isNotBlank() } ?: dto.title ?: return null
        val slug = dto.slug?.takeIf { it.isNotBlank() } ?: title
            .replace(Regex("[^a-zA-Z0-9\\u00f1\\u00d1]+"), "-")
            .trim('-')
            .lowercase()
        val streamOptions = dto.videoSources.orEmpty().flatMap { sourceGroup ->
            sourceGroup.sources.orEmpty().mapIndexed { fallbackIndex, source ->
                val sourceIndex = source.sourceIndex ?: fallbackIndex
                val label = listOfNotNull(
                    sourceGroup.group?.takeIf { it.isNotBlank() },
                    source.label?.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "UFC" }
                StreamOption(
                    label = label,
                    url = "${BuildConfig.IPTV_BASE_URL}/api/replays/$slug/stream/$sourceIndex/${source.buttonIndex}?token=${token.orEmpty()}",
                    providerId = slug,
                    quality = null,
                    provider = source.provider?.takeIf { it.isNotBlank() },
                    providerVideoId = source.providerVideoId?.takeIf { it.isNotBlank() },
                    streamFormat = source.streamFormat?.takeIf { it.isNotBlank() },
                )
            }
        }
        val matchCard = dto.matchCard.orEmpty().filter { it.isNotBlank() }
        val subtitle = matchCard.firstOrNull().orEmpty()
        val description = listOf(dto.description?.takeIf { it.isNotBlank() }, matchCard.joinToString("\n"))
            .filterNotNull()
            .joinToString("\n\n")
        return CatalogItem(
            stableId = "ufc:$slug",
            kind = ContentKind.UFC,
            title = title,
            subtitle = subtitle,
            description = description,
            imageUrl = dto.featuredImageUrl.orEmpty(),
            group = "UFC",
            badgeText = "UFC",
            streamOptions = streamOptions,
        )
    }

    /**
     * Resuelve la URL reproducible de una fuente de replay.
     *
     * Las fuentes de Dailymotion requieren una resolucion fresca: sus URL directas
     * expiran y el proxy del backend puede fallar si las cookies embebidas quedan
     * obsoletas. Igual que en walactv-desktop, se consulta el metadata de
     * Dailymotion y se devuelve la mejor calidad directa. El resto de fuentes
     * se reproducen a traves del proxy del backend.
     */
    suspend fun resolveReplayStreamUrl(option: StreamOption): String = withContext(Dispatchers.IO) {
        val isDailymotion = option.provider?.equals("dailymotion", ignoreCase = true) == true
        val videoId = option.providerVideoId?.takeIf { it.isNotBlank() }
            ?: extractDailymotionAccessId(option.url)
        if (isDailymotion && videoId != null) {
            val direct = resolveDailymotionStreamUrl(videoId)
            if (direct != null) {
                Log.d(TAG, "resolveReplayStreamUrl: dailymotion resuelto directo para $videoId")
                return@withContext direct
            }
            Log.w(TAG, "resolveReplayStreamUrl: fallo resolucion dailymotion, usando proxy")
        }
        option.url
    }

    private suspend fun resolveDailymotionStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val metadataUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
        var connection: java.net.HttpURLConnection? = null
        try {
            connection = java.net.URL(metadataUrl).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "resolveDailymotionStreamUrl: HTTP ${connection.responseCode} para $videoId")
                return@withContext null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val payload = org.json.JSONObject(body)
            val qualities = payload.optJSONObject("qualities") ?: return@withContext null
            val best = pickBestDailymotionQuality(qualities) ?: return@withContext null
            best.optString("url").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "resolveDailymotionStreamUrl failed for $videoId", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun pickBestDailymotionQuality(qualities: org.json.JSONObject): org.json.JSONObject? {
        var best: org.json.JSONObject? = null
        var bestScore = -1
        val names = qualities.keys()
        while (names.hasNext()) {
            val key = names.next()
            val sources = qualities.optJSONArray(key)
            if (sources == null || sources.length() == 0) continue
            val first = sources.optJSONObject(0) ?: continue
            if (first.optString("url").isBlank()) continue
            val score = key.toIntOrNull() ?: if (key.equals("auto", ignoreCase = true)) 0 else -1
            if (score > bestScore) {
                bestScore = score
                best = first
            }
        }
        return best
    }

    private fun extractDailymotionAccessId(url: String): String? {
        if (url.isBlank()) return null
        val patterns = listOf(
            Regex("""/embed/video/([A-Za-z0-9]+)"""),
            Regex("""/manifest/video/([A-Za-z0-9]+)\.m3u8"""),
            Regex("""/video/([A-Za-z0-9]+)\.m3u8"""),
        )
        for (pattern in patterns) {
            pattern.find(url)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    suspend fun loadFavoriteChannels(): List<CatalogItem> = withContext(Dispatchers.IO) {
        val response = apiService.getFavorites()
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}")
        val items = response.body().orEmpty().map { it.toCatalogItem(ContentKind.CHANNEL) }
        resolveStreamTemplates(items).distinctBy(CatalogItem::stableId)
    }

    // ── Busqueda ──────────────────────────────────────────────────────────────

    suspend fun search(
        query: String,
        page: Int = 1,
        pageSize: Int = 50,
        types: String? = null,
    ): Pair<List<CatalogItem>, SearchResponse> = withContext(Dispatchers.IO) {
        val password = CredentialStore.password().ifBlank { null }
        val response = apiService.search(query, page, pageSize, types, password)
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}")
        val payload = response.body() ?: throw IllegalStateException("Empty response body")
        val items = payload.items.map { it.toCatalogItem() }
        val resolved = resolveStreamTemplates(items).distinctBy(CatalogItem::stableId)

        val user = CredentialStore.username()
        val pass = CredentialStore.password()
        val withFallbacks = resolved.map { item ->
            if (item.streamOptions.isNotEmpty()) return@map item
            if (item.kind != ContentKind.CHANNEL && item.kind != ContentKind.EVENT) return@map item
            val streamId = item.providerId?.takeIf { it.isNotBlank() } ?: item.stableId
            if (streamId.isBlank()) return@map item
            val fallbackUrl = "${BuildConfig.IPTV_BASE_URL}/live/$user/$pass/$streamId"
            item.copy(
                streamOptions = listOf(
                    StreamOption(label = "Directo", url = fallbackUrl)
                )
            )
        }
        Pair(withFallbacks, payload)
    }

    suspend fun fetchContentItem(kind: ContentKind, itemId: String): CatalogItem? = withContext(Dispatchers.IO) {
        if (itemId.isBlank() || kind == ContentKind.EVENT || kind == ContentKind.CHANNEL) return@withContext null
        val response = apiService.getContentItem(kind.toApiType(), itemId)
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}")
        val dto = response.body() ?: return@withContext null
        val item = dto.toCatalogItem(kind)
        resolveStreamTemplates(listOf(item)).firstOrNull()
    }

    // ── Series / episodios ────────────────────────────────────────────────────

    suspend fun loadSeriesEpisodes(seriesName: String): List<CatalogItem> =
        withContext(Dispatchers.IO) {
            if (seriesName.isBlank()) {
                Log.w(TAG, "loadSeriesEpisodes: seriesName is blank, returning empty")
                return@withContext emptyList()
            }
            Log.d(TAG, "loadSeriesEpisodes: loading episodes for '$seriesName'")
            val items = mutableListOf<CatalogItem>()
            var page = 1
            do {
                Log.d(TAG, "loadSeriesEpisodes: fetching page $page")
                val response = try {
                    apiService.getSeriesEpisodes(seriesName, page, 100, CredentialStore.password().ifBlank { null })
                } catch (e: Exception) {
                    Log.e(TAG, "loadSeriesEpisodes: HTTP error for '$seriesName' page $page: ${e.message}")
                    break
                }
                if (!response.isSuccessful) {
                    Log.e(TAG, "loadSeriesEpisodes: HTTP ${response.code()} for '$seriesName' page $page")
                    break
                }
                val body = response.body()
                val dtos = if (body != null) {
                    body.items.ifEmpty { body.episodes }
                } else {
                    emptyList()
                }
                if (body == null) {
                    Log.e(TAG, "loadSeriesEpisodes: null body for '$seriesName' page $page")
                } else if (dtos.isEmpty()) {
                    Log.d(TAG, "loadSeriesEpisodes: empty episodes for '$seriesName' page $page (total=${body.totalEpisodes})")
                }
                val parsed = dtos.map { it.toCatalogItem(ContentKind.SERIES) }
                Log.d(TAG, "loadSeriesEpisodes: page $page returned ${parsed.size} items")
                items += parsed
                page++
                if (parsed.size < 100) break
            } while (true)
            val resolved = resolveStreamTemplates(items).distinctBy(CatalogItem::stableId)
            Log.d(TAG, "loadSeriesEpisodes: total ${resolved.size} episodes after deduplication")
            resolved
        }

    suspend fun loadSeriesEpisodesById(seriesId: String): List<CatalogItem> =
        withContext(Dispatchers.IO) {
            if (seriesId.isBlank()) {
                Log.w(TAG, "loadSeriesEpisodesById: seriesId is blank, returning empty")
                return@withContext emptyList()
            }
            Log.d(TAG, "loadSeriesEpisodesById: loading episodes for seriesId='$seriesId'")
            val items = mutableListOf<CatalogItem>()
            var page = 1
            do {
                val response = try {
                    apiService.getSeriesEpisodesById(
                        seriesId = seriesId,
                        page = page,
                        pageSize = 100,
                        password = CredentialStore.password().ifBlank { null },
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "loadSeriesEpisodesById: HTTP error for '$seriesId' page $page: ${e.message}")
                    break
                }
                if (!response.isSuccessful) {
                    Log.e(TAG, "loadSeriesEpisodesById: HTTP ${response.code()} for '$seriesId' page $page")
                    break
                }
                val body = response.body()
                val dtos = if (body != null) {
                    body.items.ifEmpty { body.episodes }
                } else {
                    emptyList()
                }
                val parsed = dtos.map { it.toCatalogItem(ContentKind.SERIES) }
                Log.d(TAG, "loadSeriesEpisodesById: page $page returned ${parsed.size} items")
                items += parsed
                page++
                if (parsed.size < 100) break
            } while (true)
            val resolved = resolveStreamTemplates(items).distinctBy(CatalogItem::stableId)
            Log.d(TAG, "loadSeriesEpisodesById: total ${resolved.size} episodes after deduplication")
            resolved
        }

    // ── Content pagination for home sections ───────────────────────────────────

    suspend fun loadContentPage(
        contentType: String,
        group: String?,
        page: Int,
        pageSize: Int = 12,
        year: Int? = null,
        sectionTitle: String? = null,
    ): Pair<List<CatalogItem>, Boolean> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val country = PreferencesManager.getPreferredLanguageOrDefault()
            val effectiveGroup = if (year != null || sectionTitle != null) null else group

            Log.d(TAG, "loadContentPage: loading $contentType group=$effectiveGroup year=$year sectionTitle=$sectionTitle page=$page")
            val response = apiService.getCatalogPage(
                contentType = contentType,
                country = country?.takeIf { it.isNotBlank() },
                group = effectiveGroup?.takeIf { it.isNotBlank() },
                year = year,
                section = sectionTitle,
                page = page,
                pageSize = pageSize,
            )
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}")
            val payload = response.body() ?: throw IllegalStateException("Empty response body")
            val expectedKind = when (contentType) {
                "movies" -> ContentKind.MOVIE
                "series" -> ContentKind.SERIES
                else -> null
            }
            val items = payload.items.map { it.toCatalogItem(expectedKind) }
            val resolved = resolveStreamTemplates(items)
            Log.d(TAG, "loadContentPage: loaded ${resolved.size} items in ${System.currentTimeMillis() - startTime}ms, hasNext=${payload.hasNext}")
            Pair(resolved, payload.hasNext)
        }

    // ── Eventos ───────────────────────────────────────────────────────────────

    suspend fun resolveEventItem(eventItem: CatalogItem): CatalogItem =
        withContext(Dispatchers.IO) {
            if (eventItem.kind != ContentKind.EVENT) return@withContext eventItem
            if (eventItem.streamOptions.isNotEmpty()) return@withContext eventItem
            eventItem
        }

    // ══════════════════════════════════════════════════════════════════════════
    // Implementacion privada
    // ══════════════════════════════════════════════════════════════════════════

    private suspend fun fetchRemoteHomeCatalog(): HomeCatalog {
        val country = PreferencesManager.getPreferredLanguageOrDefault()
        val response = apiService.getHomeCatalog(country)
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}")
        val payload = response.body() ?: throw IllegalStateException("Empty response body")
        return resolveStreamTemplates(mapHomeCatalogResponse(payload))
    }

    private suspend fun fetchEventSections(): List<BrowseSection> {
        val today = DATE_FORMATTER.format(java.time.Instant.now())
        Log.d(TAG, "fetchEventSections: requesting calendar for $today")
        val response = apiService.getCalendarEvents(
            date = today,
            password = CredentialStore.password().ifBlank { null },
            client = "android",
        )
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}")
        val payload = response.body() ?: throw IllegalStateException("Empty response body")
        Log.d(TAG, "fetchEventSections: eventos=${payload.eventos.size}")

        val items = payload.eventos.mapNotNull { mapCalendarEvent(it) }
            .sortedBy { it.badgeText }

        if (items.isEmpty()) return emptyList()

        return buildList {
            add(BrowseSection("Eventos de hoy", items))
        }
    }

    private fun mapCalendarEvent(dto: CalendarEventDto): CatalogItem? {
        val user = CredentialStore.username()
        val pass = CredentialStore.password()

        val resolvedCanales = dto.canalesResueltos
            .filter { !it.channelId.isNullOrBlank() || !it.providerId.isNullOrBlank() }
            .sortedWith(
                compareByDescending<CanalResueltoDto> { EVENT_QUALITY_ORDER[it.quality?.uppercase()?.trim()] ?: 0 }
                    .thenBy { it.priority }
            )
            .distinctBy {
                (it.channelId?.takeIf { c -> c.isNotBlank() } ?: it.providerId) to
                    (it.quality?.uppercase()?.trim().orEmpty())
            }

        val streamOptions = resolvedCanales.map { canal ->
            val url = if (!canal.streamUrl.isNullOrBlank()) {
                canal.streamUrl
            } else {
                val streamId = canal.providerId?.takeIf { it.isNotBlank() } ?: canal.channelId
                "${BuildConfig.IPTV_BASE_URL}/live/$user/$pass/$streamId"
            }
            val base = (canal.displayName?.ifBlank { null } ?: canal.sourceName?.ifBlank { null }).orEmpty()
            val quality = canal.quality?.trim().orEmpty()
            val label = when {
                quality.isBlank() -> base
                base.contains(quality, ignoreCase = true) -> base
                else -> "$base $quality"
            }
            StreamOption(
                label = label,
                url = url,
                providerId = canal.providerId,
                quality = canal.quality?.takeIf { it.isNotBlank() },
            )
        }

        if (streamOptions.isEmpty()) return null

        val title = dto.equipos?.takeIf { it.isNotBlank() } ?: dto.competicion.orEmpty()
        val group = dto.competicion?.takeIf { it.isNotBlank() }.orEmpty()
        val subtitle = buildString {
            append(dto.categoria.orEmpty())
            if (!dto.subtituloCompeticion.isNullOrBlank()) {
                if (isNotEmpty()) append(" | ")
                append(dto.subtituloCompeticion)
            }
        }
        val badgeText = dto.hora?.takeIf { it.isNotBlank() }.orEmpty()
        val imageUrl = dto.imagenEvento?.takeIf { it.isNotBlank() }.orEmpty()
        val channelNames = resolvedCanales
            .map { c -> (c.displayName?.ifBlank { null } ?: c.sourceName?.ifBlank { null }).orEmpty() }
            .map { cleanQualityLabels(it) }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .joinToString(" | ")
            .ifBlank {
                dto.canalesOriginal
                    .map { cleanQualityLabels(it) }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase(Locale.ROOT) }
                    .joinToString(" | ")
            }
        val stableId = dto.id ?: "event_${dto.hora}_${dto.equipos}"

        return CatalogItem(
            stableId = stableId,
            providerId = dto.id,
            title = title,
            subtitle = subtitle,
            description = channelNames.ifBlank { "" },
            imageUrl = imageUrl,
            kind = ContentKind.EVENT,
            group = group,
            badgeText = badgeText,
            streamOptions = streamOptions,
        )
    }

    private fun mapHomeCatalogResponse(response: HomeCatalogResponse): HomeCatalog {
        val allSections = response.sections.map { it to null } +
            response.movieSections.map { it to "movies" } +
            response.seriesSections.map { it to "series" }
        val sections = allSections.map { (section, inferredContentType) ->
            val contentType = inferredContentType ?: section.contentType
            val expectedKind = when (contentType) {
                "movies" -> ContentKind.MOVIE
                "series" -> ContentKind.SERIES
                "channels" -> ContentKind.CHANNEL
                else -> null
            }
            Log.d(TAG, "mapHome: section='${section.title}' contentType=$contentType expectedKind=$expectedKind items=${section.items.size}")
            val mappedItems = section.items.map { it.toCatalogItem(expectedKind) }
            val channelCount = mappedItems.count { it.kind == ContentKind.CHANNEL }
            Log.d(TAG, "mapHome: section='${section.title}' channelCount=$channelCount of ${mappedItems.size}")
            val items = if (channelCount > 0) mergeChannelVariants(mappedItems) else mappedItems
            Log.d(TAG, "mapHome: section='${section.title}' afterMerge=${items.size}")
            val title = section.title.orEmpty()
            val sectionTitle = section.sectionTitle
                ?: title.takeIf { t ->
                    Regex("^20\\d{2}\\s*ESTRENOS", RegexOption.IGNORE_CASE).matches(t) ||
                    Regex("^(PRIME|NETFLIX|HBO MAX|DISNEY\\+|HBO)$", RegexOption.IGNORE_CASE).matches(t)
                }
            val year = section.year
                ?: Regex("^(20\\d{2})\\s*ESTRENOS", RegexOption.IGNORE_CASE)
                    .find(title)?.groupValues?.get(1)?.toIntOrNull()
            BrowseSection(
                title = title,
                items = items,
                contentType = contentType,
                groupName = section.groupName ?: title.substringBefore(" ·").takeIf { it.isNotBlank() },
                sectionTitle = sectionTitle,
                year = year,
                hasNextPage = section.hasNext || section.items.size >= 24,
            )
        }

        val searchableItems = sections.flatMap(BrowseSection::items).distinctBy(CatalogItem::stableId)
        return HomeCatalog(sections = sections, searchableItems = searchableItems, favoriteItems = null)
    }

    // ── Resolucion de streams ─────────────────────────────────────────────────

    private fun resolveStreamTemplates(catalog: HomeCatalog): HomeCatalog = HomeCatalog(
        sections = catalog.sections.map { s -> s.copy(items = resolveStreamTemplates(s.items)) },
        searchableItems = resolveStreamTemplates(catalog.searchableItems),
        favoriteItems = catalog.favoriteItems?.let(::resolveStreamTemplates),
    )

    private fun resolveStreamTemplates(items: List<CatalogItem>): List<CatalogItem> {
        val user = CredentialStore.username()
        val pass = CredentialStore.password()
        return items.map { item ->
            item.copy(streamOptions = item.streamOptions.map { opt ->
                opt.copy(url = resolveStreamTemplate(opt.url, user, pass))
            })
        }
    }

    private fun buildChannelUrl(channelId: String): String {
        if (channelId.isBlank()) return ""
        val c = requireCredentials()
        return "${BuildConfig.IPTV_BASE_URL}/live/${c.username}/${c.password}/$channelId"
    }

    // ── Helpers de ContentKind ────────────────────────────────────────────────

    private fun ContentKind.toApiType(): String = when (this) {
        ContentKind.CHANNEL -> "channels"
        ContentKind.MOVIE   -> "movies"
        ContentKind.SERIES  -> "series"
        ContentKind.EVENT   -> error("Los eventos no tienen tipo API")
        ContentKind.UFC     -> "replays"
    }

    /**
     * Safely converts an [Any?] value (from Gson deserialization) to a [String].
     * - [Double] values like `123.0` are normalized to `"123"` (no trailing ".0")
     * - [String] values pass through as-is
     * - `null` returns `null`
     */
    private fun Any?.toSafeId(): String? = when (this) {
        is Double -> if (this == toLong().toDouble()) toLong().toString() else toString()
        is String -> this
        else -> this?.toString()
    }

    // ── DTO -> Domain mapping ─────────────────────────────────────────────────

    private fun CatalogItemDto.toCatalogItem(expectedKind: ContentKind? = null): CatalogItem {
        val type = (type ?: contentType ?: mediaType).orEmpty().trim().lowercase()
        val kind = when {
            type.isBlank() && expectedKind != null -> expectedKind
            else -> when (type) {
                "channel", "channels", "live" -> ContentKind.CHANNEL
                "event" -> ContentKind.EVENT
                "movie", "movies", "vod" -> ContentKind.MOVIE
                "series", "serie", "series_group" -> ContentKind.SERIES
                else -> ContentKind.CHANNEL
            }
        }
        val rawId = (id.toSafeId() ?: episodeId.toSafeId() ?: channelId.toSafeId()).orEmpty()
        val providerIdStr = providerId.toSafeId()?.takeIf { it.isNotBlank() }
        val catalogIdVal = rawId.takeIf { it.isNotBlank() }
        val stableIdValue = providerIdStr ?: rawId
        val stableId = if (kind == ContentKind.EVENT) stableIdValue else "${kind.name.lowercase()}:$stableIdValue"

        val rawTitle = listOf(nombre, title, name, displayName, channelName)
            .firstOrNull { !it.isNullOrBlank() }.orEmpty()

        val rawGroup = listOf(grupo, group, subtitle)
            .firstOrNull { !it.isNullOrBlank() }.orEmpty()

        val normalized = parseNormalizedMetadata(
            kind = kind,
            groupTitle = rawGroup,
            tvgName = rawTitle.replace(Regex("^\\s*\\d{1,5}\\s+"), "").trim(),
            displayName = rawTitle.replace(Regex("^\\s*\\d{1,5}\\s+"), "").trim(),
            walacLanguage = country.orEmpty(),
            walacNameNormalized = "",
            walacGroupNormalized = "",
            walacSeriesNameNormalized = seriesName.orEmpty(),
        )

        val descriptionVal = listOf(
            overview,
            this.description,
            subtitle,
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

        val backdropPathVal = backdropPath
            ?: backdrop
            ?: backdropUrl
            .takeUnless { it.isNullOrBlank() }.orEmpty()
        val backdropUrlVal = buildTmdbImageUrl(backdropPathVal, "w1280")

        val tmdbPosterPathVal = (posterPath ?: "")
            .takeIf { it.isNotBlank() && isTmdbImagePath(it) }.orEmpty()
        val tmdbPosterUrlVal = buildTmdbImageUrl(tmdbPosterPathVal, "w500")

        val releaseDateVal = releaseDate?.takeIf { it.isNotBlank() }
        val parsedYear = releaseDateVal?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
            ?: this@toCatalogItem.year

        val rawImageUrl = listOf(
            logo, logoUrl, image, imageUrl, poster, posterUrl, backdrop, backdropUrl,
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        val imageUrlVal = normalizeRemoteImageUrl(rawImageUrl).ifBlank { tmdbPosterUrlVal.orEmpty() }

        val channelDisplayName = displayName ?: channelName
        val inferredChannelNumber = Regex("^\\s*(\\d{1,5})\\s+")
            .find(rawTitle)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        val tmdbTitleVal = tmdbTitle.orEmpty()

        val stillPathVal = stillPath
            ?.takeIf { it.isNotBlank() && isTmdbImagePath(it) }
            ?.let { buildTmdbImageUrl(it, "w780") }
            ?: stillPath

        return CatalogItem(
            stableId = stableId,
            catalogId = catalogIdVal,
            providerId = providerIdStr,
            title = tmdbTitleVal.ifBlank {
                if (kind == ContentKind.CHANNEL) {
                    val channelTitle = channelDisplayName?.replace(Regex("^\\s*\\d{1,5}\\s+"), "")?.trim()
                        .orEmpty().ifBlank { normalized.displayTitle }
                    val cleaned = cleanQualityLabels(channelTitle)
                    Log.d(TAG, "toCatalogItem CHANNEL: rawTitle='${rawTitle}' channelDisplayName='$channelDisplayName' channelTitle='$channelTitle' cleaned='$cleaned' kind=$kind expectedKind=$expectedKind")
                    cleaned
                } else {
                    normalized.displayTitle
                }
            },
            normalizedTitle = null,
            subtitle = normalized.groupTitle.ifBlank { rawGroup },
            description = descriptionVal.ifBlank { normalized.groupTitle.ifBlank { rawGroup } },
            imageUrl = imageUrlVal,
            kind = kind,
            group = normalized.groupTitle.ifBlank { rawGroup },
            badgeText = badgeText ?: badge.orEmpty(),
            channelNumber = channelNumber ?: inferredChannelNumber,
            languageLabel = normalized.languageLabel?.takeIf { it.isNotBlank() },
            normalizedGroup = null,
            seriesName = normalized.seriesName?.takeIf { it.isNotBlank() },
            seriesKey = seriesKey ?: seriesName,
            seasonNumber = this@toCatalogItem.seasonNumber,
            episodeNumber = this@toCatalogItem.episodeNumber,
            streamOptions = (
                listOfNotNull(
                    streamUrl.orEmpty().takeIf { it.isNotBlank() }?.let {
                        StreamOption(
                            label = "Directo",
                            url = it,
                            providerId = streams?.firstOrNull()?.providerId,
                            language = streams?.firstOrNull()?.country,
                            quality = streams?.firstOrNull()?.quality,
                        )
                    },
                ) +
                    streams.orEmpty().mapNotNull { s ->
                        s.url.takeIf { !it.isNullOrBlank() }?.let {
                            val base = s.label ?: "Ver"
                            val q = s.quality?.trim().orEmpty()
                            val label = if (q.isBlank() || base.contains(q, ignoreCase = true)) base else "$base $q"
                            StreamOption(
                                label = label,
                                url = it,
                                providerId = s.providerId,
                                language = s.country,
                                quality = s.quality,
                            )
                        }
                    }
            ),
            overviewEn = overviewEn?.takeIf { it.isNotBlank() },
            voteAverage = rating,
            voteCount = voteCount,
            runtimeMinutes = runtimeMinutes,
            genres = genres.orEmpty(),
            countries = this@toCatalogItem.countries.orEmpty(),
            backdropUrl = backdropUrlVal,
            tmdbPosterUrl = tmdbPosterUrlVal,
            tagline = null,
            releaseDate = releaseDateVal,
            year = parsedYear,
            tmdbTitle = tmdbTitleVal.ifBlank { null },
            totalSeasons = totalSeasons,
            stillPath = stillPathVal,
            airDate = this@toCatalogItem.airDate,
            titleEn = this@toCatalogItem.titleEn,
            episodeType = this@toCatalogItem.episodeType,
        )
    }

    // ── Sesion / token ────────────────────────────────────────────────────────

    suspend fun getAccessToken(): String {
        authInterceptor.token?.let { return it }
        val c = requireCredentials()
        val response = apiService.login(c.username, c.password)
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}: ${response.errorBody()?.string()}")
        val token = response.body()?.access_token
            ?: response.body()?.token
            ?: response.body()?.access
            ?: throw IllegalStateException("Respuesta de login sin access_token")
        authInterceptor.token = token
        return token
    }

    private fun requireCredentials(): StoredCredentials {
        val user = CredentialStore.username()
        val pass = CredentialStore.password()
        check(user.isNotBlank() && pass.isNotBlank()) { "No hay sesion iniciada" }
        return StoredCredentials(user, pass)
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private fun isBaseUrlConfigured(): Boolean {
        val base = BuildConfig.IPTV_BASE_URL.trim()
        return base.isNotBlank() && !base.contains("example.invalid")
    }

    private fun buildLoginErrorMessage(e: Exception): String {
        val msg = e.message.orEmpty().trim()
        return when {
            msg.contains("Unable to resolve host", ignoreCase = true) ->
                "No se puede conectar con el servidor IPTV"
            msg.contains("timeout", ignoreCase = true) ->
                "El servidor IPTV ha tardado demasiado en responder"
            msg.contains("HTTP 401", ignoreCase = true) || msg.contains("HTTP 403", ignoreCase = true) ->
                "Usuario o contrasena incorrectos"
            msg.contains("HTTP 404", ignoreCase = true) ->
                "La ruta de login no existe en el servidor configurado"
            msg.isNotBlank() -> msg
            else -> "No se pudo iniciar sesion"
        }
    }

    private fun maskUsername(username: String): String =
        if (username.length <= 2) "**" else username.take(2) + "***"

    private suspend fun safeSectionLoad(
        name: String,
        block: suspend () -> List<BrowseSection>,
    ): List<BrowseSection> =
        runCatching { block() }
            .onFailure { Log.e(TAG, "Fallo cargando seccion $name", it) }
            .getOrDefault(emptyList())

    suspend fun getPlaybackPreference(
        contentType: String,
        catalogId: String,
    ): PlaybackPreferenceDto? = withContext(Dispatchers.IO) {
        val response = apiService.getPlaybackPreference(contentType, catalogId)
        when {
            response.isSuccessful -> response.body()
            response.code() == 404 -> null
            else -> error("Playback preference request failed: ${response.code()}")
        }
    }

    suspend fun updatePlaybackPreference(
        contentType: String,
        catalogId: String,
        body: PlaybackPreferenceUpdateBody,
    ): PlaybackPreferenceDto? = withContext(Dispatchers.IO) {
        val response = apiService.updatePlaybackPreference(contentType, catalogId, body)
        if (!response.isSuccessful) {
            error("Playback preference update failed: ${response.code()}")
        }
        response.body()
    }

    // ── Modelos privados ──────────────────────────────────────────────────────

    private data class StoredCredentials(val username: String, val password: String)

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "IptvRepository"
        private const val PAGE_SIZE = 50
        const val FAVORITES_FILTER_VALUE = "Favorites"
        const val FAVORITES_FILTER_LABEL = "Favoritos"
        private val DATE_FORMATTER: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(java.time.ZoneId.of("Europe/Madrid"))
    }
}

private fun resolveStreamTemplate(template: String, username: String, password: String): String {
    if (template.isBlank()) return ""
    return template
        .replace("{{USERNAME}}", username)
        .replace("{{PASSWORD}}", password)
}
