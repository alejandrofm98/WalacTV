package com.example.walactv

import java.text.Normalizer

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
    val languageLabel: String? = null,
    val normalizedTitle: String? = null,
    val normalizedGroup: String? = null,
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

fun displayCardTitle(item: CatalogItem): String {
    return if (item.kind == ContentKind.CHANNEL && item.channelNumber != null) {
        "${item.channelNumber}  ${item.title}"
    } else {
        item.title
    }
}

fun filterItemsByCountrySelection(items: List<CatalogItem>, country: String?): List<CatalogItem> {
    val normalizedCountry = country?.trim()?.uppercase().orEmpty()
    if (normalizedCountry.isBlank()) return items
    return items.filter { item ->
        item.idioma.trim().uppercase() == normalizedCountry ||
            item.languageLabel?.takeIf { it.isNotBlank() }?.let(::normalizeLanguageCode) == normalizedCountry ||
            normalizeLanguageCode(extractCountryFromTitle(item.title)) == normalizedCountry
    }
}

fun matchesFilterSearch(label: String, query: String): Boolean {
    return normalizeFilterSearchText(label).contains(normalizeFilterSearchText(query))
}

private fun normalizeFilterSearchText(value: String): String {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .trim()
}

private fun extractCountryFromTitle(title: String): String? {
    return Regex("^\\s*([A-Z]{2,12})\\s*[-|:]\\s*")
        .find(title.uppercase())
        ?.groupValues
        ?.getOrNull(1)
}

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
                streamOptions = emptyList(),
            )
        }
        .sortedBy { it.title }

    return groupedSeries + ungroupedEpisodes.sortedBy { it.title }
}

private data class SeriesEpisodeKey(
    val seriesName: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
)

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
            episodes.firstOrNull { normalizeLanguageCode(it.idioma) == normalizedPreferredLanguage }
                ?: episodes.firstOrNull { normalizeLanguageCode(it.languageLabel) == normalizedPreferredLanguage }
                ?: episodes.first()
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

data class BrowseSection(
    val title: String,
    val items: List<CatalogItem>,
)

data class HomeCatalog(
    val sections: List<BrowseSection>,
    val searchableItems: List<CatalogItem>,
)

data class CatalogFilterOption(
    val value: String,
    val label: String,
)

data class CatalogFilters(
    val countries: List<CatalogFilterOption> = emptyList(),
    val groups: List<CatalogFilterOption> = emptyList(),
)

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

private val SERIES_REGEX = Regex("(?i)[ST](\\d+)\\s*E(\\d+)")

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

val LANGUAGE_ALIASES = mapOf(
    "ENG" to "EN",
    "ENGLISH" to "EN",
    "EN" to "EN",
    "ES" to "ES",
    "ESP" to "ES",
    "ESPANOL" to "ES",
    "SPANISH" to "ES",
    "LA" to "LATAM",
    "LAT" to "LATAM",
    "LATAM" to "LATAM",
    "LATINO" to "LATAM",
    "VOSE" to "VOSE",
    "CAST" to "CAST",
    "CASTELLANO" to "CAST",
    "SUB" to "SUB",
    "SUBTITULADO" to "SUB",
)

fun normalizeLanguageCode(value: String?): String {
    val normalized = value?.trim()?.uppercase().orEmpty()
    return when {
        normalized.isBlank() -> "ES"
        normalized == "LAT" -> "LATAM"
        normalized == "LATINO" -> "LATAM"
        normalized.startsWith("EN") -> "EN"
        normalized.startsWith("ES") -> "ES"
        normalized.contains("LAT") -> "LATAM"
        else -> normalized
    }
}

fun languageDisplayLabel(value: String?): String {
    return when (normalizeLanguageCode(value)) {
        "EN" -> "Inglés"
        "LATAM" -> "Español Latinoamericano"
        else -> "Español"
    }
}

fun isAudioSelectorEnabled(trackCount: Int): Boolean = trackCount > 1

private val LANGUAGE_TOKEN_REGEX = Regex(
    pattern = "(?i)(?<![A-Z0-9])(LATAM|LATINO|LAT|LA|ENGLISH|ENG|EN|ESPANOL|SPANISH|ESP|ES|VOSE|CASTELLANO|CAST|SUBTITULADO|SUB)(?![A-Z0-9])",
)

private fun normalizeLanguageToken(rawValue: String?): String? {
    if (rawValue.isNullOrBlank()) return null
    val cleaned = rawValue.uppercase().replace(Regex("[^A-Z0-9]+"), "")
    return LANGUAGE_ALIASES[cleaned] ?: cleaned.takeIf { it.length in 2..3 }
}

private fun detectLanguageFromGroup(groupTitle: String): String? {
    Regex("\\|\\s*([^|]+?)\\s*\\|").findAll(groupTitle).forEach { match ->
        normalizeLanguageToken(match.groupValues[1])?.let { return it }
    }

    Regex("^\\s*([A-Z]{2,12})\\s*[-|:]").find(groupTitle.uppercase())?.let { match ->
        normalizeLanguageToken(match.groupValues[1])?.let { return it }
    }

    LANGUAGE_TOKEN_REGEX.find(groupTitle.uppercase())?.let { match ->
        return normalizeLanguageToken(match.value)
    }

    return null
}

private fun detectLanguageFromTitle(title: String): String? {
    Regex("^\\s*([A-Z]{2,12})\\s*[-|:]\\s*").find(title.uppercase())?.let { match ->
        return normalizeLanguageToken(match.groupValues[1])
    }
    return null
}

private fun removeLanguagePrefix(text: String, language: String?): String {
    if (text.isBlank() || language.isNullOrBlank()) return text.trim()
    val variants = LANGUAGE_ALIASES.filterValues { it == language }.keys + language
    val prefixRegex = Regex(
        "^\\s*(?:${variants.distinct().sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }})\\s*[-|:]\\s*",
        RegexOption.IGNORE_CASE,
    )
    return text.replace(prefixRegex, "").trim()
}

private fun normalizeGroupTitle(groupTitle: String, language: String?): String {
    if (groupTitle.isBlank()) return ""
    var cleaned = groupTitle.trim()
    if (!language.isNullOrBlank()) {
        val variants = LANGUAGE_ALIASES.filterValues { it == language }.keys + language
        variants.distinct().sortedByDescending { it.length }.forEach { variant ->
            cleaned = cleaned.replace(Regex("\\|\\s*${Regex.escape(variant)}\\s*\\|", RegexOption.IGNORE_CASE), "|")
            cleaned = cleaned.replace(Regex("^\\s*${Regex.escape(variant)}\\s*[-|:]\\s*", RegexOption.IGNORE_CASE), "")
        }
    }
    cleaned = cleaned.replace(Regex("\\|+"), "|")
    return cleaned.trim(' ', '|', '-', '_').replace(Regex("\\s+"), " ")
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
        SeriesMetadata(
            seriesName = cleanedTitle.substring(0, match.range.first).trim().removeSuffix("-").trim(),
            seasonNumber = match.groupValues.getOrNull(1)?.toIntOrNull(),
            episodeNumber = match.groupValues.getOrNull(2)?.toIntOrNull(),
        )
    } else {
        SeriesMetadata(
            seriesName = cleanedTitle,
            seasonNumber = null,
            episodeNumber = null,
        )
    }
}
