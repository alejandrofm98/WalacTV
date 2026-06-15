package com.example.walactv.shared.domain

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

private val LANGUAGE_TOKEN_REGEX = Regex(
    pattern = "(?i)(?<![A-Z0-9])(LATAM|LATINO|LAT|LA|ENGLISH|ENG|EN|ESPANOL|SPANISH|ESP|ES|VOSE|CASTELLANO|CAST|SUBTITULADO|SUB)(?![A-Z0-9])",
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
        "EN" -> "Ingl\u00e9s"
        "LATAM" -> "Espa\u00f1ol Latinoamericano"
        else -> "Espa\u00f1ol"
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
    return value.replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .trim()
}

private fun extractCountryFromTitle(title: String): String? {
    return Regex("^\\s*([A-Z]{2,12})\\s*[-|:]\\s*")
        .find(title.uppercase())
        ?.groupValues
        ?.getOrNull(1)
}

internal fun normalizeLanguageToken(rawValue: String?): String? {
    if (rawValue.isNullOrBlank()) return null
    val cleaned = rawValue.uppercase().replace(Regex("[^A-Z0-9]+"), "")
    return LANGUAGE_ALIASES[cleaned] ?: cleaned.takeIf { it.length in 2..3 }
}

internal fun detectLanguageFromGroup(groupTitle: String): String? {
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

internal fun detectLanguageFromTitle(title: String): String? {
    Regex("^\\s*([A-Z]{2,12})\\s*[-|:]\\s*").find(title.uppercase())?.let { match ->
        return normalizeLanguageToken(match.groupValues[1])
    }
    return null
}

internal fun removeLanguagePrefix(text: String, language: String?): String {
    if (text.isBlank() || language.isNullOrBlank()) return text.trim()
    val variants = LANGUAGE_ALIASES.filterValues { it == language }.keys + language
    val prefixRegex = Regex(
        "^\\s*(?:${variants.distinct().sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }})\\s*[-|:]\\s*",
        RegexOption.IGNORE_CASE,
    )
    return text.replace(prefixRegex, "").trim()
}

internal fun normalizeGroupTitle(groupTitle: String, language: String?): String {
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

fun ContentKind.toApiType(): String = when (this) {
    ContentKind.CHANNEL -> "channels"
    ContentKind.MOVIE -> "movies"
    ContentKind.SERIES -> "series"
    ContentKind.EVENT -> error("Events have no API type")
}
