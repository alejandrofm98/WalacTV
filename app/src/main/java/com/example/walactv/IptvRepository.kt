package com.example.walactv

import android.content.Context
import android.util.Log
import com.example.walactv.network.AuthInterceptor
import com.example.walactv.network.IptvApiService
import com.example.walactv.network.dto.CatalogItemDto
import com.example.walactv.network.dto.CalendarEventDto
import com.example.walactv.network.dto.CanalResueltoDto
import com.example.walactv.network.dto.FilterOptionsResponse
import com.example.walactv.network.dto.HomeCatalogResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

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
                val remoteDeferred = async {
                    runCatching { fetchRemoteHomeCatalog() }
                        .onFailure { Log.e(TAG, "Fallo cargando home remota", it) }
                        .getOrDefault(HomeCatalog(emptyList(), emptyList(), null))
                }

                val eventSections = eventsDeferred.await()
                    .map { s -> s.copy(items = resolveStreamTemplates(s.items)) }
                val remote = remoteDeferred.await()

                HomeCatalog(
                    sections = eventSections + remote.sections,
                    searchableItems = (eventSections.flatMap(BrowseSection::items) + remote.searchableItems)
                        .distinctBy(CatalogItem::stableId),
                    favoriteItems = remote.favoriteItems,
                ).also { memoryHomeCatalog = it }
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
        if (kind == ContentKind.EVENT) return@withContext CatalogFilters()

        val cacheKey = "${kind.name}|${country.orEmpty()}"
        filterCache[cacheKey]?.let { return@withContext it }

        val contentType = kind.toApiType()

        val countries: List<CatalogFilterOption>
        val countriesForGroupQuery: String

        if (country == null) {
            val response = apiService.getCountries(contentType)
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}")
            val payload = response.body() ?: FilterOptionsResponse()
            countries = payload.countries.map { CatalogFilterOption(value = it.value, label = it.label) }
            countriesForGroupQuery = countries.joinToString(",", transform = CatalogFilterOption::value)
        } else {
            val baseKey = "${kind.name}|"
            countries = filterCache[baseKey]?.countries ?: run {
                val response = apiService.getCountries(contentType)
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}")
                val payload = response.body() ?: FilterOptionsResponse()
                payload.countries.map { CatalogFilterOption(value = it.value, label = it.label) }
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
            listOf(CatalogFilterOption(FAVORITES_FILTER_VALUE, FAVORITES_FILTER_LABEL)) +
                groupsPayload.groups.map { CatalogFilterOption(value = it.value, label = it.label) }
        } else {
            groupsPayload.groups.map { CatalogFilterOption(value = it.value, label = it.label) }
        }

        val genres = if (kind == ContentKind.MOVIE || kind == ContentKind.SERIES) {
            val genresResponse = apiService.getGenres(contentType)
            if (genresResponse.isSuccessful) {
                (genresResponse.body()?.genres ?: emptyList()).map { CatalogFilterOption(value = it, label = it) }
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

    suspend fun loadFavoriteChannels(): List<CatalogItem> = withContext(Dispatchers.IO) {
        val response = apiService.getFavorites()
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}")
        val items = response.body().orEmpty().map { it.toCatalogItem(ContentKind.CHANNEL) }
        resolveStreamTemplates(items).distinctBy(CatalogItem::stableId)
    }

    // ── Busqueda ──────────────────────────────────────────────────────────────

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
        val today = DATE_FORMATTER.format(Date())
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

        if (items.isEmpty()) return emptyList()

        return buildList {
            add(BrowseSection("Eventos de hoy", items))
        }
    }

    private fun mapCalendarEvent(dto: CalendarEventDto): CatalogItem? {
        val user = CredentialStore.username()
        val pass = CredentialStore.password()

        val streamOptions = dto.canalesResueltos
            .filter { !it.channelId.isNullOrBlank() || !it.providerId.isNullOrBlank() }
            .distinctBy { it.channelId }
            .map { canal ->
                val url = if (!canal.streamUrl.isNullOrBlank()) {
                    canal.streamUrl
                } else {
                    val streamId = canal.providerId?.takeIf { it.isNotBlank() } ?: canal.channelId
                    "${BuildConfig.IPTV_BASE_URL}/live/$user/$pass/$streamId"
                }
                StreamOption(
                    label = canal.sourceName ?: canal.displayName.orEmpty(),
                    url = url,
                    providerId = canal.providerId,
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
        val channelNames = dto.canalesOriginal.joinToString(" | ")
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
            val items = section.items.map { it.toCatalogItem(expectedKind) }
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
        val rawId = (id?.toString() ?: episodeId?.toString() ?: channelId?.toString()).orEmpty()
        val providerIdStr = providerId?.toString()?.takeIf { it.isNotBlank() }
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

        return CatalogItem(
            stableId = stableId,
            providerId = providerIdStr,
            title = tmdbTitleVal.ifBlank {
                if (kind == ContentKind.CHANNEL) {
                    channelDisplayName?.replace(Regex("^\\s*\\d{1,5}\\s+"), "")?.trim()
                        .orEmpty().ifBlank { normalized.displayTitle }
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
            seriesKey = null,
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
                            StreamOption(
                                label = s.label ?: "Ver",
                                url = it,
                                providerId = s.providerId,
                                language = s.country,
                                quality = s.quality,
                            )
                        }
                    }
            ),
            overviewEn = overviewEn?.takeIf { it.isNotBlank() },
            voteAverage = rating?.toFloat(),
            voteCount = null,
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
        )
    }

    private fun isTmdbImagePath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith("http://image.tmdb.org") || path.startsWith("https://image.tmdb.org")) return true
        return path.trimStart('/').isNotBlank() && !path.trimStart('/').contains("/")
    }

    private fun normalizeRemoteImageUrl(url: String): String {
        if (url.isBlank() || url == "null") return ""
        val trimmedUrl = url.trim()
        val normalizedBaseUrl = BuildConfig.IPTV_BASE_URL.trimEnd('/')
        val normalizedUrl = when {
            trimmedUrl.startsWith("//") -> "https:$trimmedUrl"
            trimmedUrl.startsWith("/") -> "$normalizedBaseUrl$trimmedUrl"
            trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://") -> trimmedUrl
            else -> "$normalizedBaseUrl/$trimmedUrl"
        }
        return normalizedUrl
            .replace("http://${BuildConfig.IPTV_BASE_URL.removePrefix("https://").removePrefix("http://")}", BuildConfig.IPTV_BASE_URL)
            .replace("http://image.tmdb.org", "https://image.tmdb.org")
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

    // ── Modelos privados ──────────────────────────────────────────────────────

    private data class StoredCredentials(val username: String, val password: String)

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "IptvRepository"
        private const val PAGE_SIZE = 50
        const val FAVORITES_FILTER_VALUE = "Favorites"
        const val FAVORITES_FILTER_LABEL = "Favoritos"
        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
