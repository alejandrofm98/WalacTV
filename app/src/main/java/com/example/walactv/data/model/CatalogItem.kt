package com.example.walactv.data.model

import android.util.Log

data class CatalogItem(
    val stableId: String,
    val catalogId: String? = null,
    val providerId: String? = null,
    val title: String,
    val subtitle: String,
    val description: String,
    val imageUrl: String,
    val kind: ContentKind,
    val group: String,
    val badgeText: String,
    val channelNumber: Int? = null,
    val languageLabel: String? = null,
    val normalizedTitle: String? = null,
    val normalizedGroup: String? = null,
    val seriesName: String? = null,
    val seriesKey: String? = null,
    val seriesProviderId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val streamOptions: List<StreamOption> = emptyList(),
    val isWatched: Boolean = false,
    val overviewEn: String? = null,
    val voteAverage: Double? = null,
    val voteCount: Int? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val backdropUrl: String? = null,
    val tmdbPosterUrl: String? = null,
    val tagline: String? = null,
    val releaseDate: String? = null,
    val year: Int? = null,
    val tmdbTitle: String? = null,
    val totalSeasons: Int? = null,
    val stillPath: String? = null,
    val airDate: String? = null,
    val titleEn: String? = null,
    val episodeType: String? = null,
    val imdbId: String? = null,
)

private const val TMDB_IMG_TAG = "TMDB_IMG"

fun CatalogItem.searchableText(): List<String> {
    return buildList {
        add(title)
        add(subtitle)
        add(description)
        add(group)
        add(kind.name)
        channelNumber?.let { add(it.toString()) }
    }
}

fun CatalogItem.isVodContent(): Boolean = kind == ContentKind.MOVIE || kind == ContentKind.SERIES

fun CatalogItem.preferredVodPosterUrl(): String {
    val result = tmdbPosterUrl?.takeIf { it.isNotBlank() } ?: imageUrl
    Log.d(TMDB_IMG_TAG, "preferredVodPosterUrl stableId=${stableId.take(40)} kind=$kind tmdbPosterUrl=${tmdbPosterUrl.orEmpty().take(120)} imageUrl=${imageUrl.take(120)} result=${result.take(120)}")
    return result
}

fun CatalogItem.preferredCardImageUrl(): String {
    val result = if (isVodContent()) preferredVodPosterUrl() else imageUrl
    Log.d(TMDB_IMG_TAG, "preferredCardImageUrl stableId=${stableId.take(40)} kind=$kind isVod=${isVodContent()} result=${result.take(120)}")
    return result
}

val CatalogItem.idioma: String
    get() {
        languageLabel?.takeIf { it.isNotBlank() }?.let { return it }
        val separatorIndex = group.indexOfFirst { it == '|' || it == '-' }
        return if (separatorIndex != -1) {
            group.substring(0, separatorIndex).trim()
        } else {
            "Todos"
        }
    }

val CatalogItem.subgrupo: String
    get() {
        normalizedGroup?.takeIf { it.isNotBlank() }?.let { return it }
        val separatorIndex = group.indexOfFirst { it == '|' || it == '-' }
        return if (separatorIndex != -1) {
            group.substring(separatorIndex + 1).trim()
        } else {
            group.trim()
        }
    }
