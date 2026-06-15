package com.example.walactv.shared.data

import com.example.walactv.shared.domain.CatalogFilterOption
import com.example.walactv.shared.domain.CatalogFilters
import com.example.walactv.shared.domain.CatalogItem
import com.example.walactv.shared.domain.ContentKind
import com.example.walactv.shared.local.ChannelEntity
import com.example.walactv.shared.local.ContentDatabase
import com.example.walactv.shared.network.IptvApiClient
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ContentCacheManager(
    private val database: ContentDatabase,
    private val apiClient: IptvApiClient,
    private val credentialStore: CredentialStore,
    private val baseUrl: String,
) {
    suspend fun needsSyncChannels(): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                val localCount = database.channelQueries.getCount().executeAsOne()
                localCount == 0L
            } catch (_: Exception) {
                true
            }
        }
    }

    suspend fun syncChannels(): Result<Int> {
        return withContext(Dispatchers.Default) {
            try {
                val rawJson = apiClient.getFullChannelsRaw()
                val channels = parseChannelsFromJson(rawJson)
                database.channelQueries.deleteAll()
                channels.forEach { ch ->
                    database.channelQueries.insertAll(
                        id = ch.id,
                        numero = ch.numero,
                        providerId = ch.providerId,
                        logo = ch.logo,
                        countries = ch.countries,
                        nombreNormalizado = ch.nombreNormalizado,
                        grupoNormalizado = ch.grupoNormalizado,
                    )
                }
                Result.success(channels.size)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getChannelCount(): Long {
        return database.channelQueries.getCount().executeAsOne()
    }

    suspend fun getLocalChannelFilters(): CatalogFilters {
        return withContext(Dispatchers.Default) {
            val countries = database.channelQueries.getDistinctCountries().executeAsList()
                .flatMap { it.split(",") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .map { CatalogFilterOption(value = it, label = it) }

            val groups = database.channelQueries.getDistinctGroups().executeAsList()
                .filter { it.isNotBlank() }
                .map { CatalogFilterOption(value = it, label = it) }

            CatalogFilters(countries = countries, groups = groups)
        }
    }

    suspend fun getChannelsPaged(
        limit: Int,
        offset: Int,
        country: String? = null,
        group: String? = null,
    ): List<CatalogItem> {
        return withContext(Dispatchers.Default) {
            val entities = when {
                country != null && group != null ->
                    database.channelQueries.getByCountryAndGroupPaged(country, group, limit.toLong(), offset.toLong())
                country != null ->
                    database.channelQueries.getByCountryPaged(country, limit.toLong(), offset.toLong())
                group != null ->
                    database.channelQueries.getByGroupPaged(group, limit.toLong(), offset.toLong())
                else ->
                    database.channelQueries.getAllPaged(limit.toLong(), offset.toLong())
            }
            entities.executeAsList().map { it.toCatalogItem() }
        }
    }

    suspend fun searchChannels(query: String, country: String? = null, group: String? = null): List<CatalogItem> {
        return withContext(Dispatchers.Default) {
            val entities = when {
                country != null && group != null ->
                    database.channelQueries.searchByCountryAndGroup(query, country, group)
                country != null ->
                    database.channelQueries.searchByCountry(query, country)
                group != null ->
                    database.channelQueries.searchByGroup(query, group)
                else ->
                    database.channelQueries.search(query)
            }
            entities.executeAsList().map { it.toCatalogItem() }
        }
    }

    suspend fun needsSyncMovies(): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                val localCount = database.movieQueries.getCount().executeAsOne()
                localCount == 0L
            } catch (_: Exception) {
                true
            }
        }
    }

    suspend fun syncMoviesToCache(movies: List<CatalogItem>): Result<Int> {
        return withContext(Dispatchers.Default) {
            try {
                database.movieQueries.deleteAll()
                movies.forEach { item ->
                    database.movieQueries.insertAll(
                        id = item.stableId,
                        providerId = item.providerId.orEmpty(),
                        nombre = item.title,
                        logo = item.imageUrl,
                        countries = item.countries.joinToString(","),
                        nombreNormalizado = item.title,
                        grupoNormalizado = item.group,
                    )
                }
                Result.success(movies.size)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun needsSyncSeries(): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                val localCount = database.seriesQueries.getCount().executeAsOne()
                localCount == 0L
            } catch (_: Exception) {
                true
            }
        }
    }

    suspend fun syncSeriesToCache(series: List<CatalogItem>): Result<Int> {
        return withContext(Dispatchers.Default) {
            try {
                database.seriesQueries.deleteAll()
                series.forEach { item ->
                    database.seriesQueries.insertAll(
                        id = item.stableId,
                        providerId = item.providerId.orEmpty(),
                        logo = item.imageUrl,
                        countries = item.countries.joinToString(","),
                        temporada = (item.seasonNumber ?: 0).toLong(),
                        episodio = (item.episodeNumber ?: 0).toLong(),
                        serieName = item.seriesName.orEmpty().ifBlank { item.title },
                        nombreNormalizado = item.title,
                        grupoNormalizado = item.group,
                    )
                }
                Result.success(series.size)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun syncAllContent(
        movies: List<CatalogItem>,
        series: List<CatalogItem>,
        channels: List<CatalogItem>,
    ): Result<Triple<Int, Int, Int>> {
        return withContext(Dispatchers.Default) {
            try {
                database.movieQueries.deleteAll()
                movies.forEach { item ->
                    database.movieQueries.insertAll(
                        id = item.stableId,
                        providerId = item.providerId.orEmpty(),
                        nombre = item.title,
                        logo = item.imageUrl,
                        countries = item.countries.joinToString(","),
                        nombreNormalizado = item.title,
                        grupoNormalizado = item.group,
                    )
                }

                database.seriesQueries.deleteAll()
                series.forEach { item ->
                    database.seriesQueries.insertAll(
                        id = item.stableId,
                        providerId = item.providerId.orEmpty(),
                        logo = item.imageUrl,
                        countries = item.countries.joinToString(","),
                        temporada = (item.seasonNumber ?: 0).toLong(),
                        episodio = (item.episodeNumber ?: 0).toLong(),
                        serieName = item.seriesName.orEmpty().ifBlank { item.title },
                        nombreNormalizado = item.title,
                        grupoNormalizado = item.group,
                    )
                }

                val rawJson = apiClient.getFullChannelsRaw()
                val channelEntities = parseChannelsFromJson(rawJson)
                database.channelQueries.deleteAll()
                channelEntities.forEach { ch ->
                    database.channelQueries.insertAll(
                        id = ch.id,
                        numero = ch.numero,
                        providerId = ch.providerId,
                        logo = ch.logo,
                        countries = ch.countries,
                        nombreNormalizado = ch.nombreNormalizado,
                        grupoNormalizado = ch.grupoNormalizado,
                    )
                }

                Result.success(Triple(movies.size, series.size, channelEntities.size))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getMovieCount(): Long {
        return database.movieQueries.getCount().executeAsOne()
    }

    suspend fun getSeriesCount(): Long {
        return database.seriesQueries.getCount().executeAsOne()
    }

    fun getAllCachedMovies(): Flow<List<CatalogItem>> {
        return database.movieQueries.getAllPaged(Long.MAX_VALUE, 0)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toCatalogItem() } }
    }

    fun getAllCachedSeries(): Flow<List<CatalogItem>> {
        return database.seriesQueries.getAllPaged(Long.MAX_VALUE, 0)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toCatalogItem() } }
    }

    suspend fun getMoviesPaged(
        limit: Int,
        offset: Int,
        country: String? = null,
        group: String? = null,
    ): List<CatalogItem> {
        return withContext(Dispatchers.Default) {
            val entities = when {
                country != null && group != null ->
                    database.movieQueries.getByCountryAndGroupPaged(country, group, limit.toLong(), offset.toLong())
                country != null ->
                    database.movieQueries.getByCountryPaged(country, limit.toLong(), offset.toLong())
                group != null ->
                    database.movieQueries.getByGroupPaged(group, limit.toLong(), offset.toLong())
                else ->
                    database.movieQueries.getAllPaged(limit.toLong(), offset.toLong())
            }
            entities.executeAsList().map { it.toCatalogItem() }
        }
    }

    suspend fun getSeriesPaged(
        limit: Int,
        offset: Int,
        country: String? = null,
        group: String? = null,
    ): List<CatalogItem> {
        return withContext(Dispatchers.Default) {
            val entities = when {
                country != null && group != null ->
                    database.seriesQueries.getByCountryAndGroupPaged(country, group, limit.toLong(), offset.toLong())
                country != null ->
                    database.seriesQueries.getByCountryPaged(country, limit.toLong(), offset.toLong())
                group != null ->
                    database.seriesQueries.getByGroupPaged(group, limit.toLong(), offset.toLong())
                else ->
                    database.seriesQueries.getAllPaged(limit.toLong(), offset.toLong())
            }
            entities.executeAsList().map { it.toCatalogItem() }
        }
    }

    suspend fun searchMovies(query: String, country: String? = null, group: String? = null): List<CatalogItem> {
        return withContext(Dispatchers.Default) {
            database.movieQueries.search(query).executeAsList().map { it.toCatalogItem() }
        }
    }

    suspend fun searchSeries(query: String, country: String? = null, group: String? = null): List<CatalogItem> {
        return withContext(Dispatchers.Default) {
            database.seriesQueries.search(query).executeAsList().map { it.toCatalogItem() }
        }
    }

    private fun parseChannelsFromJson(rawJson: String): List<ChannelEntity> {
        val result = mutableListOf<ChannelEntity>()
        val regex = Regex("""\{[^}]*"id"\s*:\s*"?([^",}]+)"?[^}]*\}""")
        var id = ""
        var numero: Long? = null
        var providerId = ""
        var logo = ""
        var countries = ""
        var nombreNormalizado = ""
        var grupoNormalizado = ""

        for (line in rawJson.lines()) {
            val trimmed = line.trim().removeSuffix(",")
            when {
                trimmed.contains("\"id\"") -> {
                    if (id.isNotBlank() && providerId.isNotBlank()) {
                        result.add(ChannelEntity(id, numero, providerId, logo, countries, nombreNormalizado, grupoNormalizado))
                    }
                    id = extractJsonValue(trimmed, "id")
                    numero = extractJsonLong(trimmed, "num")
                    providerId = extractJsonValue(trimmed, "provider_id")
                    logo = extractJsonValue(trimmed, "logo")
                    countries = extractJsonValue(trimmed, "country")
                    nombreNormalizado = extractJsonValue(trimmed, "nombre_normalizado")
                    grupoNormalizado = extractJsonValue(trimmed, "grupo_normalizado")
                }
                trimmed.contains("\"num\"") && numero == null -> {
                    numero = extractJsonLong(trimmed, "num")
                }
                trimmed.contains("\"provider_id\"") && providerId.isBlank() -> {
                    providerId = extractJsonValue(trimmed, "provider_id")
                }
                trimmed.contains("\"logo\"") && logo.isBlank() -> {
                    logo = extractJsonValue(trimmed, "logo")
                }
                trimmed.contains("\"country\"") && countries.isBlank() -> {
                    countries = extractJsonValue(trimmed, "country")
                }
                trimmed.contains("\"nombre_normalizado\"") && nombreNormalizado.isBlank() -> {
                    nombreNormalizado = extractJsonValue(trimmed, "nombre_normalizado")
                }
                trimmed.contains("\"grupo_normalizado\"") && grupoNormalizado.isBlank() -> {
                    grupoNormalizado = extractJsonValue(trimmed, "grupo_normalizado")
                }
            }
        }
        if (id.isNotBlank() && providerId.isNotBlank()) {
            result.add(ChannelEntity(id, numero, providerId, logo, countries, nombreNormalizado, grupoNormalizado))
        }
        return result
    }

    private fun extractJsonValue(line: String, key: String): String {
        val pattern = Regex(""""$key"\s*:\s*"([^"]*)"""")
        return pattern.find(line)?.groupValues?.getOrNull(1) ?: ""
    }

    private fun extractJsonLong(line: String, key: String): Long? {
        val pattern = Regex(""""$key"\s*:\s*(\d+)""")
        return pattern.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun buildStreamProxyPath(kind: String, providerId: String): String {
        val user = credentialStore.username()
        val pass = credentialStore.password()
        return if (providerId.isNotBlank() && baseUrl.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
            "$baseUrl/$kind/$user/$pass/$providerId"
        } else ""
    }

    private fun ChannelEntity.toCatalogItem(): CatalogItem {
        val streamUrl = buildStreamProxyPath("live", providerId)
        return CatalogItem(
            stableId = id,
            providerId = providerId,
            title = nombreNormalizado,
            subtitle = "",
            description = "",
            imageUrl = logo,
            kind = ContentKind.CHANNEL,
            group = grupoNormalizado,
            badgeText = "",
            channelNumber = numero?.toInt(),
            countries = countries.split(",").map { it.trim() }.filter { it.isNotBlank() },
            streamOptions = if (streamUrl.isNotBlank()) listOf(
                com.example.walactv.shared.domain.StreamOption(label = "Ver", url = streamUrl, providerId = providerId)
            ) else emptyList(),
        )
    }

    private fun com.example.walactv.shared.local.MovieEntity.toCatalogItem(): CatalogItem {
        val streamUrl = buildStreamProxyPath("movie", providerId)
        return CatalogItem(
            stableId = id,
            providerId = providerId,
            title = nombreNormalizado,
            subtitle = "",
            description = "",
            imageUrl = logo,
            kind = ContentKind.MOVIE,
            group = grupoNormalizado,
            badgeText = "",
            countries = countries.split(",").map { it.trim() }.filter { it.isNotBlank() },
            streamOptions = if (streamUrl.isNotBlank()) listOf(
                com.example.walactv.shared.domain.StreamOption(label = "Ver", url = streamUrl, providerId = providerId)
            ) else emptyList(),
        )
    }

    private fun com.example.walactv.shared.local.SeriesEntity.toCatalogItem(): CatalogItem {
        val streamUrl = buildStreamProxyPath("series", providerId)
        return CatalogItem(
            stableId = id,
            providerId = providerId,
            title = nombreNormalizado,
            subtitle = "",
            description = "",
            imageUrl = logo,
            kind = ContentKind.SERIES,
            group = grupoNormalizado,
            badgeText = "",
            countries = countries.split(",").map { it.trim() }.filter { it.isNotBlank() },
            seriesName = serieName.ifBlank { null },
            seasonNumber = temporada.toInt().takeIf { it > 0 },
            episodeNumber = episodio.toInt().takeIf { it > 0 },
            streamOptions = if (streamUrl.isNotBlank()) listOf(
                com.example.walactv.shared.domain.StreamOption(label = "Ver", url = streamUrl, providerId = providerId)
            ) else emptyList(),
        )
    }
}
