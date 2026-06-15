package com.example.walactv.shared.domain

import com.example.walactv.shared.network.dto.CatalogItemDto
import kotlinx.serialization.json.*

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

fun CatalogItemDto.toCatalogItem(expectedKind: ContentKind? = null, baseUrl: String = ""): CatalogItem {
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
    val rawId = (id ?: episodeId ?: channelId).orEmpty()
    val providerIdStr = providerId?.takeIf { it.isNotBlank() }
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
        ?: this.year

    val rawImageUrl = listOf(
        logo, logoUrl, image, imageUrl, poster, posterUrl, backdrop, backdropUrl,
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
    val imageUrlVal = normalizeRemoteImageUrl(rawImageUrl, baseUrl).ifBlank { tmdbPosterUrlVal.orEmpty() }

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
        seriesKey = seriesKey ?: seriesName,
        seasonNumber = this.seasonNumber,
        episodeNumber = this.episodeNumber,
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
        voteCount = voteCount,
        runtimeMinutes = runtimeMinutes,
        genres = genres.orEmpty(),
        countries = this.countries.orEmpty(),
        backdropUrl = backdropUrlVal,
        tmdbPosterUrl = tmdbPosterUrlVal,
        tagline = null,
        releaseDate = releaseDateVal,
        year = parsedYear,
        tmdbTitle = tmdbTitleVal.ifBlank { null },
        totalSeasons = totalSeasons,
        stillPath = stillPathVal,
        airDate = this.airDate,
        titleEn = this.titleEn,
        episodeType = this.episodeType,
    )
}

fun List<CatalogItemDto>.toCatalogItems(expectedKind: ContentKind? = null, baseUrl: String = ""): List<CatalogItem> {
    return map { it.toCatalogItem(expectedKind, baseUrl) }
}

fun mapHomeCatalogResponse(
    sections: List<com.example.walactv.shared.network.dto.SectionDto>,
    movieSections: List<com.example.walactv.shared.network.dto.SectionDto>,
    seriesSections: List<com.example.walactv.shared.network.dto.SectionDto>,
    baseUrl: String = "",
): HomeCatalog {
    val allSections = sections.map { it to null } +
        movieSections.map { it to "movies" } +
        seriesSections.map { it to "series" }
    val browseSections = allSections.map { (section, inferredContentType) ->
        val contentType = inferredContentType ?: section.contentType
        val expectedKind = when (contentType) {
            "movies" -> ContentKind.MOVIE
            "series" -> ContentKind.SERIES
            "channels" -> ContentKind.CHANNEL
            else -> null
        }
        val items = section.items.toCatalogItems(expectedKind, baseUrl)
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
            groupName = section.groupName ?: title.substringBefore(" \u00b7").takeIf { it.isNotBlank() },
            sectionTitle = sectionTitle,
            year = year,
            hasNextPage = section.hasNext || section.items.size >= 24,
        )
    }
    val searchableItems = browseSections.flatMap(BrowseSection::items).distinctBy(CatalogItem::stableId)
    return HomeCatalog(sections = browseSections, searchableItems = searchableItems, favoriteItems = null)
}

private fun isTmdbImagePath(path: String): Boolean {
    if (path.isBlank()) return false
    if (path.startsWith("http://image.tmdb.org") || path.startsWith("https://image.tmdb.org")) return true
    return path.trimStart('/').isNotBlank() && !path.trimStart('/').contains("/")
}

private fun normalizeRemoteImageUrl(url: String, baseUrl: String): String {
    if (url.isBlank() || url == "null") return ""
    val trimmedUrl = url.trim()
    val normalizedBaseUrl = baseUrl.trimEnd('/')
    return when {
        trimmedUrl.startsWith("//") -> "https:$trimmedUrl"
        trimmedUrl.startsWith("/") -> "$normalizedBaseUrl$trimmedUrl"
        trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://") -> trimmedUrl
        else -> "$normalizedBaseUrl/$trimmedUrl"
    }.replace("http://image.tmdb.org", "https://image.tmdb.org")
}
