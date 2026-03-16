package com.example.walactv

enum class ContentKind {
    EVENT,
    CHANNEL,
    MOVIE,
    SERIES,
}

data class StreamOption(
    val label: String,
    val url: String,
)

data class CatalogItem(
    val stableId: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val imageUrl: String,
    val kind: ContentKind,
    val group: String,
    val badgeText: String,
    val channelNumber: Int? = null,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val streamOptions: List<StreamOption>,
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

data class BrowseSection(
    val title: String,
    val items: List<CatalogItem>,
)

data class HomeCatalog(
    val sections: List<BrowseSection>,
    val searchableItems: List<CatalogItem>,
)

val CatalogItem.idioma: String
    get() {
        val separatorIndex = group.indexOfFirst { it == '|' || it == '-' }
        return if (separatorIndex != -1) {
            group.substring(0, separatorIndex).trim()
        } else {
            "Todos"
        }
    }

val CatalogItem.subgrupo: String
    get() {
        val separatorIndex = group.indexOfFirst { it == '|' || it == '-' }
        return if (separatorIndex != -1) {
            group.substring(separatorIndex + 1).trim()
        } else {
            group.trim()
        }
    }

private val SERIES_REGEX = Regex("(?i)[ST](\\d+)\\s*E(\\d+)")

data class SeriesMetadata(
    val seriesName: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
)

fun parseSeriesMetadata(title: String, kind: ContentKind): SeriesMetadata {
    if (kind != ContentKind.SERIES) {
        return SeriesMetadata(
            seriesName = title,
            seasonNumber = null,
            episodeNumber = null,
        )
    }

    val match = SERIES_REGEX.find(title)
    return if (match != null) {
        SeriesMetadata(
            seriesName = title.substring(0, match.range.first).trim().removeSuffix("-").trim(),
            seasonNumber = match.groupValues.getOrNull(1)?.toIntOrNull(),
            episodeNumber = match.groupValues.getOrNull(2)?.toIntOrNull(),
        )
    } else {
        SeriesMetadata(
            seriesName = title,
            seasonNumber = null,
            episodeNumber = null,
        )
    }
}
