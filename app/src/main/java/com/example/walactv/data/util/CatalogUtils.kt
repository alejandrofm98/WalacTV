package com.example.walactv.data.util

import java.text.Normalizer
import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.model.idioma

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
        "EN" -> "Inglés"
        "LATAM" -> "Español Latinoamericano"
        else -> "Español"
    }
}

/**
 * Variante corta para insignias de las listas de fuentes (el drawer no tiene
 * sitio para "Español Latinoamericano").
 */
fun languageBadgeLabel(value: String?): String {
    return when (normalizeLanguageCode(value)) {
        "EN" -> "Inglés"
        "ES" -> "Español"
        "LATAM" -> "Latino"
        "CAST" -> "Castellano"
        "VOSE" -> "VOSE"
        "SUB" -> "Subtitulado"
        else -> languageDisplayLabel(value)
    }
}

private val DISPLAYABLE_LANGUAGE_CODES = setOf("EN", "ES", "LATAM", "CAST", "VOSE", "SUB")

/**
 * Traduce la etiqueta de una fuente cuando es un codigo de idioma pelado
 * ("EN", "ES", "ENG"...) tal como lo manda el backend. Cualquier otro texto
 * ("Servidor principal", "Ver", "Directo") se devuelve intacto.
 */
fun translateBareLanguageCode(rawLabel: String?): String {
    val text = rawLabel?.trim().orEmpty()
    if (text.isEmpty() || text.length > 12 || text.any { it.isWhitespace() }) return text
    val normalized = normalizeLanguageCode(text)
    if (normalized !in DISPLAYABLE_LANGUAGE_CODES) return text
    val upper = text.uppercase()
    val isBareCode = upper == normalized || LANGUAGE_ALIASES[upper] == normalized
    return if (isBareCode) languageBadgeLabel(text) else text
}

private val SEASON_MARKER_REGEX = Regex("""\bS(\d{1,2})\b""", RegexOption.IGNORE_CASE)
private val PACK_WORD_REGEX = Regex("""COMPLETE|COMPLETA|\bPACK\b|\bSEASON\b|TEMPORADA""", RegexOption.IGNORE_CASE)
private val EPISODE_MARKER_REGEX =
    Regex("""\bE\d{1,3}\b|\bEP\.?\s*\d+|\bCAP\.?\s*\d+|CAPITULO|EPISODIO|EPISODE""", RegexOption.IGNORE_CASE)

/**
 * Detecta si el titulo de un torrent es un pack de temporada ("Silo.S03...",
 * "Breaking Bad Season 2 COMPLETE") en vez de un capitulo suelto ("S03E03",
 * "Cap.303"). Sirve para avisar de que el tamaño mostrado es el del pack
 * completo y solo se descargara el archivo del capitulo (fileIdx).
 */
fun isSeasonPackTitle(title: String?): Boolean {
    val text = title?.trim().orEmpty()
    if (text.isEmpty()) return false
    if (PACK_WORD_REGEX.containsMatchIn(text)) return true
    if (!SEASON_MARKER_REGEX.containsMatchIn(text)) return false
    return !EPISODE_MARKER_REGEX.containsMatchIn(text)
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
