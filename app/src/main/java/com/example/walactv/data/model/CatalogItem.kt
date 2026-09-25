package com.example.walactv.data.model


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
    val overviewEs: String? = null,
    val voteAverage: Double? = null,
    val voteCount: Int? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val backdropUrl: String? = null,
    val titleLogoUrl: String? = null,
    val tmdbPosterUrl: String? = null,
    val tagline: String? = null,
    val releaseDate: String? = null,
    val lastAirDate: String? = null,
    val year: Int? = null,
    val status: String? = null,
    val tmdbTitle: String? = null,
    val totalSeasons: Int? = null,
    val stillPath: String? = null,
    val airDate: String? = null,
    val titleEn: String? = null,
    val episodeType: String? = null,
    val imdbId: String? = null,
    val skipSegments: SkipSegments? = null,
)


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
    return tmdbPosterUrl?.takeIf { it.isNotBlank() } ?: imageUrl
}

fun CatalogItem.displaySynopsis(): String {
    return overviewEs?.takeIf { it.isNotBlank() && it != group }
        ?: description.takeIf { it.isNotBlank() && it != group }
        ?: overviewEn?.takeIf { it.isNotBlank() && it != group }
        ?: ""
}

fun CatalogItem.displayYearLabel(): String? {
    val startYear = year ?: releaseDate?.take(4)?.toIntOrNull() ?: return null
    if (kind != ContentKind.SERIES) return startYear.toString()

    val endYear = lastAirDate?.take(4)?.toIntOrNull()
    val normalizedStatus = status?.trim()?.lowercase()
    val isEnded = normalizedStatus in setOf(
        "ended", "canceled", "cancelled", "finalizada", "finalizado", "cancelada", "cancelado",
    )

    return when {
        !isEnded && !status.isNullOrBlank() -> "$startYear -"
        endYear != null && endYear > startYear -> "$startYear - $endYear"
        else -> startYear.toString()
    }
}

fun CatalogItem.preferredCardImageUrl(): String {
    // Landscape catalog cards look sharper with the scraped TMDB backdrop.
    val result = if (isVodContent()) {
        backdropUrl?.takeIf { it.isNotBlank() } ?: preferredVodPosterUrl()
    } else {
        imageUrl
    }
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
