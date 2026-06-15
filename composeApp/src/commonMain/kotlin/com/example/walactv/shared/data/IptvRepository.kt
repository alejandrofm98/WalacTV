package com.example.walactv.shared.data

import com.example.walactv.shared.domain.*
import com.example.walactv.shared.network.IptvApiClient
import com.example.walactv.shared.network.dto.*

class IptvRepository(
    private val apiClient: IptvApiClient,
    private val credentialStore: CredentialStore,
    private val preferencesManager: PreferencesManager,
    private val tokenStore: TokenStore,
    private val baseUrl: String,
) {
    private val filterCache = mutableMapOf<String, CatalogFilters>()
    private var memoryHomeCatalog: HomeCatalog? = null

    suspend fun signIn(username: String, password: String) {
        val user = username.trim()
        val pass = password.trim()
        require(user.isNotBlank() && pass.isNotBlank()) { "Introduce usuario y contrasena" }
        require(baseUrl.isNotBlank() && !baseUrl.contains("example.invalid")) { "Falta configurar la URL del servidor" }

        val response = apiClient.login(user, pass)
        val token = response.accessToken ?: response.token ?: response.access
            ?: throw IllegalStateException("Respuesta de login sin access_token")
        tokenStore.save(token)
        credentialStore.save(user, pass)
        clearAllCaches()
    }

    fun signOut() {
        tokenStore.clear()
        credentialStore.clear()
        clearAllCaches()
    }

    private fun clearAllCaches() {
        memoryHomeCatalog = null
        filterCache.clear()
    }

    suspend fun loadHomeCatalog(forceRefresh: Boolean = false): HomeCatalog {
        if (!forceRefresh) memoryHomeCatalog?.let { return it }

        val country = preferencesManager.getPreferredLanguageOrDefault()
        val response = apiClient.getHomeCatalog(country)
        val catalog = mapHomeCatalogResponse(
            sections = response.sections,
            movieSections = response.movieSections,
            seriesSections = response.seriesSections,
            baseUrl = baseUrl,
        )
        val resolved = resolveStreamTemplates(catalog)
        memoryHomeCatalog = resolved
        return resolved
    }

    suspend fun loadCatalogFilters(kind: ContentKind, country: String? = null): CatalogFilters {
        if (kind == ContentKind.EVENT) return CatalogFilters()

        val cacheKey = "${kind.name}|${country.orEmpty()}"
        filterCache[cacheKey]?.let { return it }

        val contentType = kind.toApiType()
        val countries = apiClient.getCountries(contentType).countries
            .map { CatalogFilterOption(value = it.value, label = it.label) }
        val countriesForGroupQuery = country?.takeIf { it.isNotBlank() }
            ?: countries.joinToString(",", transform = CatalogFilterOption::value)

        val groupsResponse = apiClient.getGroups(contentType, countriesForGroupQuery)
        val groups = if (kind == ContentKind.CHANNEL) {
            listOf(CatalogFilterOption("Favorites", "Favoritos")) +
                groupsResponse.groups.map { CatalogFilterOption(value = it.value, label = it.label) }
        } else {
            groupsResponse.groups.map { CatalogFilterOption(value = it.value, label = it.label) }
        }

        val genres = if (kind == ContentKind.MOVIE || kind == ContentKind.SERIES) {
            try {
                apiClient.getGenres(contentType).genres.map { CatalogFilterOption(value = it, label = it) }
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()

        return CatalogFilters(countries = countries, groups = groups, genres = genres)
            .also { filterCache[cacheKey] = it }
    }

    suspend fun loadCatalogPage(
        kind: ContentKind,
        page: Int,
        country: String? = null,
        group: String? = null,
        search: String? = null,
        genre: String? = null,
    ): RemoteCatalogPage {
        if (kind == ContentKind.EVENT) {
            return RemoteCatalogPage(emptyList(), 0, page, 0, 0, false, false)
        }

        if (kind == ContentKind.CHANNEL && group == "Favorites") {
            val favorites = apiClient.getFavorites()
            val items = favorites.map { it.toCatalogItem(ContentKind.CHANNEL, baseUrl) }
            val resolved = resolveStreamTemplates(items).distinctBy(CatalogItem::stableId)
            return RemoteCatalogPage(resolved, resolved.size, 1, resolved.size, 1, false, false)
        }

        val response = apiClient.getCatalogPage(
            contentType = kind.toApiType(),
            country = country?.takeIf { it.isNotBlank() },
            group = group?.takeIf { it.isNotBlank() },
            search = search?.takeIf { it.isNotBlank() },
            genre = genre?.takeIf { it.isNotBlank() },
            page = page,
        )
        val items = response.items.toCatalogItems(kind, baseUrl)
        val resolved = resolveStreamTemplates(items).distinctBy(CatalogItem::stableId)
        return RemoteCatalogPage(
            items = resolved,
            total = response.total,
            page = response.page,
            pageSize = response.pageSize,
            pages = response.pages,
            hasNext = response.hasNext,
            hasPrev = response.hasPrev,
        )
    }

    suspend fun search(query: String, page: Int = 1, pageSize: Int = 50, types: String? = null): List<CatalogItem> {
        val password = credentialStore.password().ifBlank { null }
        val response = apiClient.search(query, page, pageSize, types, password)
        val items = response.items.toCatalogItems(baseUrl = baseUrl)
        val resolved = resolveStreamTemplates(items).distinctBy(CatalogItem::stableId)
        val user = credentialStore.username()
        val pass = credentialStore.password()
        return resolved.map { item ->
            if (item.streamOptions.isNotEmpty()) return@map item
            if (item.kind != ContentKind.CHANNEL && item.kind != ContentKind.EVENT) return@map item
            val streamId = item.providerId?.takeIf { it.isNotBlank() } ?: item.stableId
            if (streamId.isBlank()) return@map item
            val fallbackUrl = "${baseUrl.trimEnd('/')}/live/$user/$pass/$streamId"
            item.copy(streamOptions = listOf(StreamOption(label = "Directo", url = fallbackUrl)))
        }
    }

    suspend fun fetchContentItem(kind: ContentKind, itemId: String): CatalogItem? {
        if (itemId.isBlank() || kind == ContentKind.EVENT || kind == ContentKind.CHANNEL) return null
        val dto = apiClient.getContentItem(kind.toApiType(), itemId)
        val item = dto.toCatalogItem(kind, baseUrl)
        return resolveStreamTemplates(listOf(item)).firstOrNull()
    }

    suspend fun loadSeriesEpisodes(seriesName: String): List<CatalogItem> {
        if (seriesName.isBlank()) return emptyList()
        val items = mutableListOf<CatalogItem>()
        var page = 1
        do {
            val response = try {
                apiClient.getSeriesEpisodes(seriesName, page, 100, credentialStore.password().ifBlank { null })
            } catch (_: Exception) {
                break
            }
            val dtos = response.items.ifEmpty { response.episodes }
            if (dtos.isEmpty()) break
            val parsed = dtos.toCatalogItems(ContentKind.SERIES, baseUrl)
            items += parsed
            page++
            if (parsed.size < 100) break
        } while (true)
        return resolveStreamTemplates(items).distinctBy(CatalogItem::stableId)
    }

    suspend fun loadFavoriteChannels(): List<CatalogItem> {
        val favorites = apiClient.getFavorites()
        val items = favorites.map { it.toCatalogItem(ContentKind.CHANNEL, baseUrl) }
        return resolveStreamTemplates(items).distinctBy(CatalogItem::stableId)
    }

    suspend fun updateChannelFavorite(item: CatalogItem, isFavorite: Boolean) {
        if (item.kind != ContentKind.CHANNEL) return
        val favoriteId = item.providerId?.takeIf { it.isNotBlank() }
            ?: item.stableId.substringAfter("channel:", item.stableId)
        if (favoriteId.isBlank()) return
        if (isFavorite) apiClient.addFavorite(favoriteId) else apiClient.removeFavorite(favoriteId)
    }

    suspend fun getAccessToken(): String {
        val cached = tokenStore.get()
        if (cached != null) return cached

        val user = credentialStore.username()
        val pass = credentialStore.password()
        if (user.isBlank() || pass.isBlank()) throw IllegalStateException("No hay sesion iniciada")
        val response = apiClient.login(user, pass)
        val token = response.accessToken ?: response.token ?: response.access
            ?: throw IllegalStateException("No se pudo obtener token")
        tokenStore.save(token)
        return token
    }

    fun currentUsername(): String = credentialStore.username()
    fun currentPassword(): String = credentialStore.password()

    private fun resolveStreamTemplates(catalog: HomeCatalog): HomeCatalog = HomeCatalog(
        sections = catalog.sections.map { s -> s.copy(items = resolveStreamTemplates(s.items)) },
        searchableItems = resolveStreamTemplates(catalog.searchableItems),
        favoriteItems = catalog.favoriteItems?.let(::resolveStreamTemplates),
    )

    private fun resolveStreamTemplates(items: List<CatalogItem>): List<CatalogItem> {
        val user = credentialStore.username()
        val pass = credentialStore.password()
        return items.map { item ->
            item.copy(streamOptions = item.streamOptions.map { opt ->
                opt.copy(url = resolveStreamTemplate(opt.url, user, pass))
            })
        }
    }
}
