package com.example.walactv.data.util

import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.remote.api.dto.WatchProgressDto
import java.text.Normalizer

private val SERIES_LANGUAGE_PREFIX_REGEX = Regex(
    "^\\s*(?:${LANGUAGE_ALIASES.keys.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) }})\\s*[-|:]\\s*",
    RegexOption.IGNORE_CASE,
)

internal fun buildSeriesEpisodeProgressMap(
    episodes: List<CatalogItem>,
    progressItems: List<WatchProgressDto>,
    seriesIds: Set<String>,
    seriesNames: Set<String>,
): Map<Pair<Int, Int>, WatchProgressDto> {
    val episodeKeys = episodes.mapNotNull(CatalogItem::episodeKey).toSet()
    val episodeKeyById = buildMap {
        episodes.forEach { episode ->
            val key = episode.episodeKey() ?: return@forEach
            episode.episodeIds().forEach { id -> put(id, key) }
        }
    }
    val normalizedSeriesIds = seriesIds.mapNotNull(::normalizeId).toSet()
    val normalizedSeriesNames = seriesNames.mapNotNull(::normalizeSeriesName).toSet()

    return buildMap {
        progressItems.asSequence()
            .filter { it.contentType?.trim()?.equals("series", ignoreCase = true) == true }
            .forEach { progress ->
                val idMatchedKey = progress.episodeIds()
                    .firstNotNullOfOrNull(episodeKeyById::get)
                val reportedKey = progress.seasonNumber?.let { season ->
                    progress.episodeNumber?.let { episode -> season to episode }
                }
                val key = idMatchedKey ?: reportedKey?.takeIf {
                    it in episodeKeys && progress.belongsToSeries(normalizedSeriesIds, normalizedSeriesNames)
                } ?: return@forEach

                put(key, preferredProgress(get(key), progress))
            }
    }
}

private fun CatalogItem.episodeKey(): Pair<Int, Int>? =
    seasonNumber?.let { season -> episodeNumber?.let { episode -> season to episode } }

private fun CatalogItem.episodeIds(): Set<String> = buildSet {
    listOf(catalogId, providerId, stableId).mapNotNullTo(this, ::normalizeId)
    streamOptions.mapNotNullTo(this) { normalizeId(it.providerId) }
}

private fun WatchProgressDto.episodeIds(): Set<String> =
    listOf(contentId, providerId).mapNotNullTo(mutableSetOf(), ::normalizeId)

private fun WatchProgressDto.belongsToSeries(
    seriesIds: Set<String>,
    seriesNames: Set<String>,
): Boolean {
    val matchingId = listOf(seriesProviderId, contentId)
        .mapNotNull(::normalizeId)
        .any(seriesIds::contains)
    if (matchingId) return true

    return listOf(seriesName, normalizedTitle, title)
        .mapNotNull(::normalizeSeriesName)
        .any(seriesNames::contains)
}

private fun preferredProgress(
    current: WatchProgressDto?,
    candidate: WatchProgressDto,
): WatchProgressDto = when {
    current == null -> candidate
    current.isWatched == true && candidate.isWatched != true -> current
    candidate.isWatched == true && current.isWatched != true -> candidate
    candidate.lastWatchedAt.orEmpty() > current.lastWatchedAt.orEmpty() -> candidate
    else -> current
}

private fun normalizeId(value: String?): String? = value
    ?.trim()
    ?.takeIf(String::isNotBlank)
    ?.substringAfterLast(':')

private fun normalizeSeriesName(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(SERIES_LANGUAGE_PREFIX_REGEX, "")
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .ifBlank { null }
}
