package com.example.walactv

private val SERIES_REGEX = Regex("(?i)[ST](\\d+)\\s*E(\\d+)")

private val QUALITY_REGEX = Regex(
    "\\s*[\\[(]\\s*(UHD|FHD|HD|SD|4K|HEVC|H265|HQ|LQ)\\s*[\\])]\\s*|\\b(UHD|FHD|HD|SD|4K|HEVC|H265|HQ|LQ)\\b",
    RegexOption.IGNORE_CASE,
)

private val QUALITY_ORDER = mapOf(
    "UHD" to 7, "4K" to 6, "FHD" to 5, "HD" to 4, "SD" to 3, "HQ" to 2, "LQ" to 1,
)

private fun extractQualityLabel(title: String): String? {
    val match = QUALITY_REGEX.find(title) ?: return null
    return (match.groupValues[1].ifBlank { match.groupValues[2] }).uppercase().ifBlank { null }
}

private fun cleanQualityLabels(title: String): String {
    var cleaned = QUALITY_REGEX.replace(title, " ")
    cleaned = cleaned.replace(Regex("\\s*\\[\\s*\\]\\s*"), " ")
    cleaned = cleaned.replace(Regex("\\s*\\(\\s*\\)\\s*"), " ")
    return cleaned.replace(Regex("\\s+"), " ").trim()
}

private fun qualityScore(item: CatalogItem): Int {
    return extractQualityLabel(item.title)
        ?.let { QUALITY_ORDER[it] ?: 0 }
        ?: 0
}

private data class SeriesEpisodeKey(
    val seriesName: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
)

data class SeriesMetadata(
    val seriesName: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
)

data class NormalizedMetadata(
    val languageLabel: String?,
    val displayTitle: String,
    val groupTitle: String,
    val seriesName: String?,
)

fun buildSeriesGridItems(items: List<CatalogItem>): List<CatalogItem> {
    val (groupedEpisodes, ungroupedEpisodes) = items.partition { it.seasonNumber != null }
    val groupedSeries = groupedEpisodes.groupBy { it.seriesName ?: it.title }
        .map { (seriesName, episodes) ->
            val firstEpisode = episodes.first()
            CatalogItem(
                stableId = "series_group:$seriesName",
                title = seriesName,
                subtitle = firstEpisode.group,
                description = firstEpisode.description,
                imageUrl = firstEpisode.imageUrl,
                kind = ContentKind.SERIES,
                group = firstEpisode.group,
                badgeText = "",
                seriesName = seriesName,
                totalSeasons = firstEpisode.totalSeasons,
                streamOptions = emptyList(),
                overviewEn = firstEpisode.overviewEn,
                voteAverage = firstEpisode.voteAverage,
                voteCount = firstEpisode.voteCount,
                runtimeMinutes = firstEpisode.runtimeMinutes,
                genres = firstEpisode.genres,
                backdropUrl = firstEpisode.backdropUrl,
                tmdbPosterUrl = firstEpisode.tmdbPosterUrl,
                tagline = firstEpisode.tagline,
                releaseDate = firstEpisode.releaseDate,
                year = firstEpisode.year,
                tmdbTitle = firstEpisode.tmdbTitle,
            )
        }
        .sortedBy { it.title }

    return groupedSeries + ungroupedEpisodes.sortedBy { it.title }
}

fun List<CatalogItem>.uniqueSeriesEpisodes(preferredLanguage: String? = null): List<CatalogItem> {
    val normalizedPreferredLanguage = normalizeLanguageCode(preferredLanguage)
    return this
        .groupBy {
            SeriesEpisodeKey(
                seriesName = it.seriesName ?: it.title,
                seasonNumber = it.seasonNumber,
                episodeNumber = it.episodeNumber,
            )
        }
        .values
        .map { episodes ->
            val languageMatched = episodes.firstOrNull {
                normalizeLanguageCode(it.idioma) == normalizedPreferredLanguage ||
                normalizeLanguageCode(it.languageLabel) == normalizedPreferredLanguage
            }
            val candidates = languageMatched?.let { listOf(it) } ?: episodes
            if (candidates.size == 1) {
                candidates.first()
            } else {
                val best = candidates.maxByOrNull { qualityScore(it) } ?: candidates.first()
                val qualityOptions = candidates.flatMap { variant ->
                    val quality = extractQualityLabel(variant.title) ?: "Ver"
                    variant.streamOptions.map { it.copy(label = quality) }
                }.distinctBy { it.url }
                    .sortedByDescending { QUALITY_ORDER[it.label] ?: 0 }
                best.copy(
                    streamOptions = qualityOptions,
                    badgeText = candidates.mapNotNull { extractQualityLabel(it.title) }
                        .distinct()
                        .sortedByDescending { QUALITY_ORDER[it] ?: 0 }
                        .joinToString(" | "),
                )
            }
        }
        .sortedWith(compareBy({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }, { it.title }))
}

fun List<CatalogItem>.findEquivalentSeriesEpisode(current: CatalogItem, targetLanguage: String): CatalogItem? {
    val normalizedTargetLanguage = normalizeLanguageCode(targetLanguage)
    return this.firstOrNull { episode ->
        episode.stableId != current.stableId &&
            episode.seriesName == current.seriesName &&
            episode.seasonNumber == current.seasonNumber &&
            episode.episodeNumber == current.episodeNumber &&
            (normalizeLanguageCode(episode.idioma) == normalizedTargetLanguage ||
                normalizeLanguageCode(episode.languageLabel) == normalizedTargetLanguage)
    }
}

fun parseNormalizedMetadata(
    kind: ContentKind,
    groupTitle: String,
    tvgName: String,
    displayName: String,
    walacLanguage: String,
    walacNameNormalized: String,
    walacGroupNormalized: String,
    walacSeriesNameNormalized: String,
): NormalizedMetadata {
    val language = normalizeLanguageToken(walacLanguage)
        ?: detectLanguageFromGroup(groupTitle)
        ?: detectLanguageFromTitle(tvgName)
        ?: detectLanguageFromTitle(displayName)

    val rawDisplayTitle = displayName.ifBlank { tvgName }
    val normalizedTitle = walacNameNormalized.ifBlank {
        removeLanguagePrefix(rawDisplayTitle, language)
    }.ifBlank { rawDisplayTitle }

    val normalizedGroup = walacGroupNormalized.ifBlank {
        normalizeGroupTitle(groupTitle, language)
    }.ifBlank { groupTitle.trim() }

    val seriesName = when {
        walacSeriesNameNormalized.isNotBlank() -> walacSeriesNameNormalized
        kind == ContentKind.SERIES -> parseSeriesMetadata(normalizedTitle, kind, language).seriesName
        else -> null
    }

    return NormalizedMetadata(
        languageLabel = language,
        displayTitle = normalizedTitle,
        groupTitle = normalizedGroup,
        seriesName = seriesName,
    )
}

fun parseSeriesMetadata(title: String, kind: ContentKind, language: String? = null): SeriesMetadata {
    if (kind != ContentKind.SERIES) {
        return SeriesMetadata(
            seriesName = title,
            seasonNumber = null,
            episodeNumber = null,
        )
    }

    val cleanedTitle = removeLanguagePrefix(title, language)
    val match = SERIES_REGEX.find(cleanedTitle)
    return if (match != null) {
        val rawSeriesName = cleanedTitle.substring(0, match.range.first).trim().removeSuffix("-").trim()
        SeriesMetadata(
            seriesName = cleanQualityLabels(rawSeriesName),
            seasonNumber = match.groupValues.getOrNull(1)?.toIntOrNull(),
            episodeNumber = match.groupValues.getOrNull(2)?.toIntOrNull(),
        )
    } else {
        SeriesMetadata(
            seriesName = cleanQualityLabels(cleanedTitle),
            seasonNumber = null,
            episodeNumber = null,
        )
    }
}
