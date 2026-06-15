package com.example.walactv.shared.network

import com.example.walactv.shared.network.dto.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.parameters
import io.ktor.http.contentType

class IptvApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {

    suspend fun login(username: String, password: String): LoginResponse {
        return httpClient.submitForm(
            url = "${baseUrl.trimEnd('/')}/api/auth/login",
            formParameters = parameters {
                append("username", username)
                append("password", password)
            },
        ).body()
    }

    suspend fun getHomeCatalog(country: String? = null): HomeCatalogResponse {
        return httpClient.get("${baseUrl.trimEnd('/')}/api/home") {
            country?.let { url.parameters.append("country", it) }
        }.body()
    }

    suspend fun getCalendarEvents(
        date: String,
        password: String? = null,
        client: String? = null,
    ): CalendarResponse {
        return httpClient.get("${baseUrl.trimEnd('/')}/api/calendar/$date") {
            password?.let { url.parameters.append("password", it) }
            client?.let { url.parameters.append("client", it) }
        }.body()
    }

    suspend fun getCatalogPage(
        contentType: String,
        country: String? = null,
        group: String? = null,
        year: Int? = null,
        section: String? = null,
        search: String? = null,
        genre: String? = null,
        page: Int = 1,
        pageSize: Int = 24,
    ): CatalogPageResponse {
        return httpClient.get("${baseUrl.trimEnd('/')}/api/content") {
            url.parameters.append("content_type", contentType)
            country?.let { url.parameters.append("country", it) }
            group?.let { url.parameters.append("group", it) }
            year?.let { url.parameters.append("year", it.toString()) }
            section?.let { url.parameters.append("section_title", it) }
            search?.let { url.parameters.append("search", it) }
            genre?.let { url.parameters.append("genre", it) }
            url.parameters.append("page", page.toString())
            url.parameters.append("page_size", pageSize.toString())
        }.body()
    }

    suspend fun getContentItem(kind: String, id: String): CatalogItemResponse {
        return httpClient.get("${baseUrl.trimEnd('/')}/api/content/$kind/$id").body()
    }

    suspend fun getCountries(contentType: String): FilterOptionsResponse {
        return httpClient.get("${baseUrl.trimEnd('/')}/api/content/countries") {
            url.parameters.append("content_type", contentType)
        }.body()
    }

    suspend fun getGroups(
        contentType: String,
        countries: String? = null,
        country: String? = null,
    ): FilterOptionsResponse {
        return httpClient.get("${baseUrl.trimEnd('/')}/api/content/groups") {
            url.parameters.append("content_type", contentType)
            countries?.let { url.parameters.append("countries", it) }
            country?.let { url.parameters.append("country", it) }
        }.body()
    }

    suspend fun getGenres(contentType: String): GenresResponse {
        return httpClient.get("${baseUrl.trimEnd('/')}/api/content/genres") {
            url.parameters.append("content_type", contentType)
        }.body()
    }

    suspend fun getContentStats(contentType: String): ContentStatsResponse {
        return httpClient.get("${baseUrl.trimEnd('/')}/api/content/stats") {
            url.parameters.append("content_type", contentType)
        }.body()
    }

    suspend fun getSeriesEpisodes(
        name: String,
        page: Int = 1,
        pageSize: Int = 100,
        password: String? = null,
    ): SeriesEpisodesResponse {
        return httpClient.get("${baseUrl.trimEnd('/')}/api/series/$name/episodes") {
            url.parameters.append("page", page.toString())
            url.parameters.append("page_size", pageSize.toString())
            password?.let { url.parameters.append("password", it) }
        }.body()
    }

    suspend fun search(
        query: String,
        page: Int = 1,
        pageSize: Int = 50,
        types: String? = null,
        password: String? = null,
    ): SearchResponse {
        return httpClient.get("${baseUrl.trimEnd('/')}/api/search") {
            url.parameters.append("q", query)
            url.parameters.append("page", page.toString())
            url.parameters.append("page_size", pageSize.toString())
            types?.let { url.parameters.append("types", it) }
            password?.let { url.parameters.append("password", it) }
        }.body()
    }

    suspend fun getFavorites(): List<CatalogItemResponse> {
        return httpClient.get("${baseUrl.trimEnd('/')}/api/channel-favorites").body()
    }

    suspend fun addFavorite(channelId: String) {
        httpClient.submitForm(
            url = "${baseUrl.trimEnd('/')}/api/channel-favorites",
            formParameters = parameters {
                append("channel_id", channelId)
            },
        )
    }

    suspend fun removeFavorite(channelId: String) {
        httpClient.delete("${baseUrl.trimEnd('/')}/api/channel-favorites/$channelId")
    }

    suspend fun getWatchProgress(limit: Int = 20): WatchProgressListResponse {
        return httpClient.get("${baseUrl.trimEnd('/')}/api/watch-progress") {
            url.parameters.append("limit", limit.toString())
        }.body()
    }

    suspend fun getWatchProgressItem(id: String): WatchProgressDto {
        return httpClient.get("${baseUrl.trimEnd('/')}/api/watch-progress/$id").body()
    }

    suspend fun saveWatchProgress(id: String, body: SaveWatchProgressBody): WatchProgressDto {
        return httpClient.put("${baseUrl.trimEnd('/')}/api/watch-progress/$id") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
    }

    suspend fun deleteWatchProgress(id: String) {
        httpClient.delete("${baseUrl.trimEnd('/')}/api/watch-progress/$id")
    }

    suspend fun markWatched(id: String): WatchProgressDto {
        return httpClient.post("${baseUrl.trimEnd('/')}/api/watch-progress/$id/mark-watched").body()
    }

    suspend fun getWatchedItems(limit: Int = 200): WatchProgressListResponse {
        return httpClient.get("${baseUrl.trimEnd('/')}/api/watch-progress/watched") {
            url.parameters.append("limit", limit.toString())
        }.body()
    }

    suspend fun getFullChannelsRaw(): String {
        return httpClient.get("${baseUrl.trimEnd('/')}/api/full/channels").body()
    }

    suspend fun fetchAllMovies(country: String = "all"): List<CatalogItemDto> {
        val allItems = mutableListOf<CatalogItemDto>()
        var page = 1
        do {
            val response: CatalogPageResponse = httpClient.get("${baseUrl.trimEnd('/')}/api/content") {
                url.parameters.append("content_type", "movies")
                url.parameters.append("country", country)
                url.parameters.append("page", page.toString())
                url.parameters.append("page_size", "500")
            }.body()
            allItems += response.items
            if (!response.hasNext || response.items.isEmpty()) break
            page++
        } while (true)
        return allItems
    }

    suspend fun fetchAllSeries(country: String = "all"): List<CatalogItemDto> {
        val allItems = mutableListOf<CatalogItemDto>()
        var page = 1
        do {
            val response: CatalogPageResponse = httpClient.get("${baseUrl.trimEnd('/')}/api/content") {
                url.parameters.append("content_type", "series")
                url.parameters.append("country", country)
                url.parameters.append("page", page.toString())
                url.parameters.append("page_size", "500")
            }.body()
            allItems += response.items
            if (!response.hasNext || response.items.isEmpty()) break
            page++
        } while (true)
        return allItems
    }
}
