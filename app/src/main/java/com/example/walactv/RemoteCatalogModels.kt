package com.example.walactv

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class RemoteCatalogPage(
    val items: List<CatalogItem>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val pages: Int,
    val hasNext: Boolean,
    val hasPrev: Boolean,
)

fun resolveStreamTemplate(template: String, username: String, password: String): String {
    if (template.isBlank()) return ""
    return template
        .replace("{{USERNAME}}", username)
        .replace("{{PASSWORD}}", password)
}

fun parseRemoteHomeCatalog(payload: JSONObject): HomeCatalog {
    val favoriteItems = payload.optCatalogItems(
        "favorites",
        "favorite_channels",
        "favorites_row",
        expectedKind = ContentKind.CHANNEL,
    )
    val sections = buildList {
        addGroupedSections(payload.optJSONArray("movie_sections"), ContentKind.MOVIE)
        addGroupedSections(payload.optJSONArray("series_sections"), ContentKind.SERIES)
    }

    val searchableItems = (favoriteItems.orEmpty() + sections.flatMap(BrowseSection::items)).distinctBy(CatalogItem::stableId)
    logHomeCatalogParse(payload, sections, searchableItems)
    return HomeCatalog(sections = sections, searchableItems = searchableItems, favoriteItems = favoriteItems)
}

private fun MutableList<BrowseSection>.addGroupedSections(sectionsArray: JSONArray?, expectedKind: ContentKind) {
    if (sectionsArray == null) return
    
    val contentType = when (expectedKind) {
        ContentKind.MOVIE -> "movies"
        ContentKind.SERIES -> "series"
        else -> null
    }
    
    for (i in 0 until sectionsArray.length()) {
        val sectionObj = sectionsArray.optJSONObject(i) ?: continue
        val title = sectionObj.optString("title").takeIf { it.isNotBlank() } ?: continue
        val items = sectionObj.optJSONArray("items").toCatalogItems(expectedKind)
        
        if (items.isNotEmpty()) {
            val groupName = title.substringBefore(" ·").takeIf { it.isNotBlank() }
            val year = extractYearFromTitle(title)
            val sectionTitle = title.takeIf { isHomeSectionTitle(it) }
            val hasMore = if (sectionObj.has("has_more")) sectionObj.optBoolean("has_more") else items.size >= 24
            add(BrowseSection(title, items, contentType = contentType, groupName = groupName, year = year, sectionTitle = sectionTitle, hasNextPage = hasMore))
        }
    }
}

private fun extractYearFromTitle(title: String): Int? {
    val yearPattern = Regex("^(20\\d{2})\\s*ESTRENOS", RegexOption.IGNORE_CASE)
    return yearPattern.find(title)?.groupValues?.get(1)?.toIntOrNull()
}

private fun isHomeSectionTitle(title: String): Boolean {
    // Detecta si es una sección del home (año, PRIME, NETFLIX, HBO, DISNEY+, etc.)
    val homeSectionPatterns = listOf(
        Regex("^20\\d{2}\\s*ESTRENOS", RegexOption.IGNORE_CASE),
        Regex("^(PRIME|NETFLIX|HBO MAX|DISNEY\\+|HBO)$", RegexOption.IGNORE_CASE)
    )
    return homeSectionPatterns.any { it.matches(title) }
}

fun parseRemoteCatalogPage(payload: JSONObject, expectedKind: ContentKind? = null): RemoteCatalogPage {
    val items = payload.optJSONArray("items").toCatalogItems(expectedKind)
    val total = payload.optInt("total")
    val page = payload.optInt("page", 1)
    val pageSize = payload.optInt("page_size", 0)
    val pages = payload.optInt("pages", 0)
    val hasNext = when {
        payload.has("has_next") -> payload.optBoolean("has_next")
        pages > 0 -> page < pages
        total > 0 && pageSize > 0 -> page * pageSize < total
        else -> false
    }
    val hasPrev = when {
        payload.has("has_prev") -> payload.optBoolean("has_prev")
        pages > 0 -> page > 1
        total > 0 && pageSize > 0 -> page > 1
        else -> false
    }

    logCatalogPageParse(payload, expectedKind, items, page, total)

    return RemoteCatalogPage(
        items = items,
        total = total,
        page = page,
        pageSize = pageSize,
        pages = pages,
        hasNext = hasNext,
        hasPrev = hasPrev,
    )
}

fun parseRemoteCatalogItem(payload: JSONObject, expectedKind: ContentKind? = null): CatalogItem {
    return payload.toCatalogItem(expectedKind)
}

fun parseRemoteFilterOptions(payload: JSONObject, key: String): List<CatalogFilterOption> {
    val values = payload.optJSONArray(key) ?: JSONArray()
    return buildList {
        for (index in 0 until values.length()) {
            val rawValue = values.opt(index)
            when (rawValue) {
                is JSONObject -> {
                    val value = rawValue.optString("code")
                        .ifBlank { rawValue.optString("value") }
                        .ifBlank { rawValue.optString("id") }
                        .trim()
                    val label = rawValue.optString("name")
                        .ifBlank { rawValue.optString("label") }
                        .ifBlank { rawValue.optString("display_name") }
                        .ifBlank { value }
                        .trim()
                    if (value.isNotBlank() && label.isNotBlank()) {
                        add(CatalogFilterOption(value = value, label = label))
                    }
                }

                else -> {
                    val value = values.optString(index).trim()
                    if (value.isNotBlank()) add(CatalogFilterOption(value = value, label = value))
                }
            }
        }
    }
        .distinctBy(CatalogFilterOption::value)
}

fun buildRemoteCatalogFilters(
    kind: ContentKind,
    countriesPayload: JSONObject? = null,
    groupsPayload: JSONObject? = null,
): CatalogFilters {
    if (kind == ContentKind.EVENT) return CatalogFilters()
    return CatalogFilters(
        countries = parseRemoteFilterOptions(countriesPayload ?: JSONObject(), "countries"),
        groups = parseRemoteFilterOptions(groupsPayload ?: JSONObject(), "groups"),
    )
}

fun buildCatalogQuery(
    contentType: String,
    page: Int,
    country: String? = null,
    group: String? = null,
    search: String? = null,
): String {
    val countryParam = country?.takeIf { it.isNotBlank() }?.let { "&country=${URLEncoder.encode(it, Charsets.UTF_8.name())}" }.orEmpty()
    val groupParam = group?.takeIf { it.isNotBlank() }?.let { "&group=${URLEncoder.encode(it, Charsets.UTF_8.name())}" }.orEmpty()
    val searchParam = search?.takeIf { it.isNotBlank() }?.let { "&search=${URLEncoder.encode(it, Charsets.UTF_8.name())}" }.orEmpty()
    return "content_type=$contentType&page=$page&page_size=50$countryParam$groupParam$searchParam"
}

fun buildGroupsQuery(
    contentType: String,
    countries: String,
): String {
    return "content_type=$contentType&countries=${URLEncoder.encode(countries, Charsets.UTF_8.name())}"
}

private fun MutableList<BrowseSection>.addSectionIfNotEmpty(title: String, items: JSONArray?, expectedKind: ContentKind? = null) {
    val catalogItems = items.toCatalogItems(expectedKind)
    if (catalogItems.isNotEmpty()) {
        add(BrowseSection(title, catalogItems))
    }
}

private fun MutableList<BrowseSection>.addSeriesSectionIfNotEmpty(title: String, items: JSONArray?, expectedKind: ContentKind? = null) {
    val catalogItems = items.toCatalogItems(expectedKind)
        .groupBy { it.seriesName ?: it.title }
        .map { (seriesName, episodes) ->
            val firstEpisode = episodes.first()
            firstEpisode.copy(
                stableId = "series_group:$seriesName",
                title = firstEpisode.tmdbTitle ?: seriesName,
                subtitle = firstEpisode.group,
                description = firstEpisode.description,
                streamOptions = emptyList(),
                seriesName = seriesName,
                seasonNumber = null,
                episodeNumber = null,
            )
        }
    if (catalogItems.isNotEmpty()) {
        add(BrowseSection(title, catalogItems))
    }
}

private fun JSONArray?.toCatalogItems(expectedKind: ContentKind? = null): List<CatalogItem> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            add(getJSONObject(index).toCatalogItem(expectedKind))
        }
    }
}

private fun logHomeCatalogParse(
    payload: JSONObject,
    sections: List<BrowseSection>,
    searchableItems: List<CatalogItem>,
) {
    val containsColores = payload.toString().contains("colores", ignoreCase = true)
    if (!containsColores) return
    Log.d(
        "TMDB_PARSE",
        "home containsColores=true movieSections=${payload.optJSONArray("movie_sections")?.length() ?: 0} " +
            "seriesSections=${payload.optJSONArray("series_sections")?.length() ?: 0} sections=${sections.size} " +
            "searchable=${searchableItems.size} coloresItems=${searchableItems.filter(::isDebugColoresItem).joinToString(" || ") { it.tmdbDebugLine() }}",
    )
}

private fun logCatalogPageParse(
    payload: JSONObject,
    expectedKind: ContentKind?,
    items: List<CatalogItem>,
    page: Int,
    total: Int,
) {
    val containsColores = payload.toString().contains("colores", ignoreCase = true) || items.any(::isDebugColoresItem)
    if (!containsColores) return
    Log.d(
        "TMDB_PARSE",
        "page expectedKind=$expectedKind page=$page total=$total parsed=${items.size} " +
            "rootKeys=${payload.keys().asSequence().joinToString(",")} coloresItems=${items.filter(::isDebugColoresItem).joinToString(" || ") { it.tmdbDebugLine() }}",
    )
}

private fun isDebugColoresItem(item: CatalogItem): Boolean {
    return item.title.contains("colores", ignoreCase = true) ||
        item.normalizedTitle.orEmpty().contains("colores", ignoreCase = true) ||
        item.tmdbTitle.orEmpty().contains("colores", ignoreCase = true)
}

private fun CatalogItem.tmdbDebugLine(): String {
    return "id=$stableId provider=$providerId kind=$kind title=${title.take(100)} normalized=${normalizedTitle.orEmpty().take(100)} " +
        "tmdb=${tmdbTitle.orEmpty().take(100)} desc=${description.take(120)} overview=${overviewEn.orEmpty().take(120)} " +
        "image=${imageUrl.take(140)} poster=${tmdbPosterUrl.orEmpty().take(140)} backdrop=${backdropUrl.orEmpty().take(140)} " +
        "rating=$voteAverage runtime=$runtimeMinutes genres=${genres.joinToString("|").take(120)}"
}

private fun JSONObject.optCatalogItems(
    vararg keys: String,
    expectedKind: ContentKind? = null,
): List<CatalogItem>? {
    keys.forEach { key ->
        if (has(key)) {
            return optJSONArray(key).toCatalogItems(expectedKind)
        }
    }
    return null
}

private fun JSONObject.toCatalogItem(expectedKind: ContentKind? = null): CatalogItem {
    val type = optCleanString("type")
        .ifBlank { optCleanString("content_type") }
        .ifBlank { optCleanString("media_type") }
        .trim()
        .lowercase()
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
    val rawId = optCleanString("id").ifBlank { optCleanString("channel_id") }
    val providerId = optCleanString("provider_id").ifBlank { null }
    val stableIdValue = providerId ?: rawId
    val stableId = if (kind == ContentKind.EVENT) stableIdValue else "${kind.name.lowercase()}:$stableIdValue"
    val streamUrl = optCleanString("stream_url")

    val rawTitle = optCleanString("nombre").ifBlank {
        optCleanString("title").ifBlank {
            optCleanString("name").ifBlank {
                optCleanString("display_name").ifBlank {
                    optCleanString("channel_name")
                }
            }
        }
    }
    val nombreNormalizado = optCleanString("nombre_normalizado")
        .ifBlank { optCleanString("normalized_title") }
    val tmdbTitle = optFirstCleanString(
        "tmdb_title",
        "tmdb_name",
        "tmdb_original_title",
        "original_title",
        "original_name",
    )

    val inferredChannelNumber = Regex("^\\s*(\\d{1,5})\\s+")
        .find(rawTitle)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    val titleWithoutChannelNumber = rawTitle.replace(Regex("^\\s*\\d{1,5}\\s+"), "").trim()
    val channelDisplayName = optCleanString("display_name")
        .ifBlank { optCleanString("channel_name") }

    val rawGroup = optCleanString("grupo").ifBlank {
        optCleanString("group").ifBlank {
            optCleanString("subtitle")
        }
    }
    val grupoNormalizado = optCleanString("grupo_normalizado")
        .ifBlank { optCleanString("normalized_group") }

    val normalized = parseNormalizedMetadata(
        kind = kind,
        groupTitle = rawGroup,
        tvgName = titleWithoutChannelNumber,
        displayName = titleWithoutChannelNumber,
        walacLanguage = optCleanString("country"),
        walacNameNormalized = nombreNormalizado,
        walacGroupNormalized = grupoNormalizado,
        walacSeriesNameNormalized = optCleanString("series_name").ifBlank { optCleanString("serie_name") },
    )
    val description = optFirstCleanString(
        "overview",
        "overview_es",
        "tmdb_overview",
        "tmdb_overview_es",
        "overview_en",
        "tmdb_overview_en",
        "description",
        "subtitle",
    )

    // Parsear géneros (viene como array de strings o JSON)
    val genresList = buildList {
        if (has("genres")) {
            val genresArray = optJSONArray("genres")
            if (genresArray != null) {
                for (i in 0 until genresArray.length()) {
                    val genre = genresArray.optString(i, "")
                    if (genre.isNotBlank()) add(genre)
                }
            }
        }
    }

    // Construir URL de backdrop
    val backdropPath = optFirstCleanString(
        "tmdb_backposter",
        "tmdb_backdrop_path",
        "tmdb_backdrop",
        "backposter",
        "backdrop_path",
        "backdrop",
        "backdrop_url",
        "tmdb_backdrop_url",
    )
    val backdropUrl = buildTmdbImageUrl(backdropPath, "w1280")

    // Construir URL de poster alternativo de TMDB
    val tmdbPosterPath = optFirstCleanString(
        "tmdb_poster_path",
        "tmdb_poster",
        "tmdb_poster_url",
        "poster_path",
    ).takeIf(::isTmdbImagePath).orEmpty()
    val tmdbPosterUrl = buildTmdbImageUrl(tmdbPosterPath, "w500")

    // Parsear fecha y extraer año
    val releaseDate = optCleanString("release_date").ifBlank { null }
    val parsedYear = releaseDate?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
        ?: optInt("year").takeIf { has("year") }

    val rawImageUrl = optFirstImageString()
    val imageUrl = normalizeRemoteImageUrl(rawImageUrl).ifBlank { tmdbPosterUrl.orEmpty() }
    logVodTmdbParse(
        kind = kind,
        title = rawTitle,
        tmdbTitle = tmdbTitle,
        description = description,
        rawImageUrl = rawImageUrl,
        backdropPath = backdropPath,
        backdropUrl = backdropUrl.orEmpty(),
        tmdbPosterPath = tmdbPosterPath,
        tmdbPosterUrl = tmdbPosterUrl.orEmpty(),
        imageUrl = imageUrl,
    )

    return CatalogItem(
        stableId = stableId,
        providerId = providerId,
        title = tmdbTitle.ifBlank { buildRemoteDisplayTitle(kind, normalized.displayTitle, channelDisplayName) },
        normalizedTitle = nombreNormalizado.ifBlank { null },
        subtitle = normalized.groupTitle.ifBlank { rawGroup },
        description = description.ifBlank { normalized.groupTitle.ifBlank { rawGroup } },
        imageUrl = imageUrl,
        kind = kind,
        group = normalized.groupTitle.ifBlank { rawGroup },
        badgeText = optCleanString("badge_text").ifBlank { optCleanString("quality") },
        channelNumber = optInt("num").takeIf { has("num") } ?: inferredChannelNumber,
        languageLabel = normalized.languageLabel?.takeIf { it.isNotBlank() },
        normalizedGroup = grupoNormalizado.ifBlank { null },
        seriesName = normalized.seriesName?.takeIf { it.isNotBlank() },
        seasonNumber = optCleanString("temporada").toIntOrNull()
            ?: optInt("season_number").takeIf { has("season_number") },
        episodeNumber = optCleanString("episodio").toIntOrNull()
            ?: optInt("episode_number").takeIf { has("episode_number") },
        streamOptions = listOfNotNull(
            streamUrl.takeIf { it.isNotBlank() }?.let {
                StreamOption(
                    label = optCleanString("stream_label").ifBlank { defaultStreamLabel(kind) },
                    url = it,
                )
            },
        ),
        // Campos TMDB
        overviewEn = optFirstCleanString("overview_en", "tmdb_overview_en").ifBlank { null },
        voteAverage = optDoubleValue("vote_average")?.toFloat()
            ?: optDoubleValue("rating")?.toFloat(),
        voteCount = optInt("vote_count").takeIf { has("vote_count") },
        runtimeMinutes = optInt("runtime_minutes").takeIf { has("runtime_minutes") },
        genres = genresList,
        backdropUrl = backdropUrl,
        tmdbPosterUrl = tmdbPosterUrl,
        tagline = optCleanString("tagline").ifBlank { null },
        releaseDate = releaseDate,
        year = parsedYear,
        tmdbTitle = tmdbTitle.ifBlank { null },
        totalSeasons = optCleanString("total_seasons").toIntOrNull()
            ?: optInt("total_seasons").takeIf { has("total_seasons") && it > 0 },
    )
}

private fun JSONObject.optCleanString(key: String): String {
    return optString(key)
        .takeUnless { it.equals("null", ignoreCase = true) }
        ?.trim()
        .orEmpty()
}

private fun JSONObject.optFirstCleanString(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key -> optCleanString(key).takeIf(String::isNotBlank) }.orEmpty()
}

private fun JSONObject.optDoubleValue(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return optDouble(key).takeUnless { it.isNaN() }
}

private fun buildTmdbImageUrl(path: String, size: String): String? {
    if (path.isBlank()) return null
    if (path.startsWith("http://") || path.startsWith("https://")) return normalizeRemoteImageUrl(path)
    val normalizedPath = if (path.startsWith("/")) path else "/$path"
    return "https://image.tmdb.org/t/p/$size$normalizedPath"
}

private fun JSONObject.optFirstImageString(): String {
    val keys = listOf(
        "logo",
        "image_url",
        "logo_url",
        "stream_icon",
        "poster",
        "poster_url",
        "cover",
        "cover_url",
        "thumbnail",
        "thumbnail_url",
        "image",
        "img",
    )
    return keys.firstNotNullOfOrNull { key -> optCleanString(key).takeIf(String::isNotBlank) }.orEmpty()
}

private fun JSONObject.logVodTmdbParse(
    kind: ContentKind,
    title: String,
    tmdbTitle: String,
    description: String,
    rawImageUrl: String,
    backdropPath: String,
    backdropUrl: String,
    tmdbPosterPath: String,
    tmdbPosterUrl: String,
    imageUrl: String,
) {
    if (kind != ContentKind.MOVIE && kind != ContentKind.SERIES) return
    val shouldLog = title.contains("colores", ignoreCase = true) || imageUrl.isBlank()
    if (!shouldLog) return

    val knownTmdbValues = listOf(
        "tmdb_title",
        "tmdb_name",
        "tmdb_original_title",
        "original_title",
        "original_name",
        "overview",
        "overview_es",
        "tmdb_overview",
        "tmdb_overview_es",
        "overview_en",
        "tmdb_overview_en",
        "logo",
        "image_url",
        "logo_url",
        "stream_icon",
        "poster",
        "poster_url",
        "cover",
        "cover_url",
        "thumbnail",
        "thumbnail_url",
        "image",
        "img",
        "poster_path",
        "tmdb_poster_path",
        "tmdb_poster",
        "tmdb_poster_url",
        "tmdb_backposter",
        "tmdb_backdrop_path",
        "tmdb_backdrop",
        "backposter",
        "backdrop_path",
        "backdrop",
        "backdrop_url",
        "tmdb_backdrop_url",
    ).mapNotNull { key ->
        optCleanString(key).takeIf(String::isNotBlank)?.let { value -> "$key=${value.take(160)}" }
    }

    Log.d(
        "VOD_IMAGE_PARSE",
        "kind=$kind title=${title.take(120)} keys=${keys().asSequence().joinToString(",")} " +
            "knownTmdb=${knownTmdbValues.joinToString(" | ")} tmdbTitle=${tmdbTitle.take(120)} " +
            "hasDescription=${description.isNotBlank()} rawImage=${rawImageUrl.take(160)} " +
            "backdropPath=${backdropPath.take(160)} backdropUrl=${backdropUrl.take(160)} " +
            "tmdbPosterPath=${tmdbPosterPath.take(160)} tmdbPosterUrl=${tmdbPosterUrl.take(160)} finalImage=${imageUrl.take(160)}",
    )

    if (title.contains("colores", ignoreCase = true)) {
        Log.d("TMDB_PARSE", "rawColoresJson=${toString().take(4000)}")
    }
}

private fun isTmdbImagePath(path: String): Boolean {
    if (path.isBlank()) return false
    if (path.startsWith("http://image.tmdb.org") || path.startsWith("https://image.tmdb.org")) return true
    return path.trimStart('/').isNotBlank() && !path.trimStart('/').contains("/")
}

private fun buildRemoteDisplayTitle(kind: ContentKind, normalizedTitle: String, channelDisplayName: String): String {
    if (kind != ContentKind.CHANNEL) return normalizedTitle
    val display = channelDisplayName.replace(Regex("^\\s*\\d{1,5}\\s+"), "").trim()
    return display.ifBlank { normalizedTitle }
}

private fun defaultStreamLabel(kind: ContentKind): String {
    return when (kind) {
        ContentKind.CHANNEL,
        ContentKind.EVENT,
        -> "Directo"
        ContentKind.MOVIE -> "Reproducir"
        ContentKind.SERIES -> "Episodio"
    }
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
