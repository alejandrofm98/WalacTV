package com.example.walactv

private val KNOWN_QUALITY_TOKENS = setOf("UHD", "4K", "FHD", "HD", "SD", "HEVC", "H265", "HQ", "LQ")

data class StreamOption(
    val label: String,
    val url: String,
    val providerId: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val language: String? = null,
    val quality: String? = null,
)

data class UnifiedStreamOption(
    val language: String,
    val quality: String,
    val url: String,
    val providerId: String? = null,
    val headers: Map<String, String> = emptyMap(),
) {
    val displayLabel: String get() = "$language $quality"
}

fun List<StreamOption>.toUnifiedOptions(): List<UnifiedStreamOption> {
    return this.mapNotNull { stream ->
        val lang = stream.language ?: return@mapNotNull null
        val quality = stream.quality?.takeIf { it.uppercase() in KNOWN_QUALITY_TOKENS }
            ?: stream.label.uppercase().takeIf { it in KNOWN_QUALITY_TOKENS }
            ?: "HD"
        val displayLang = languageDisplayLabel(normalizeLanguageCode(lang))
        UnifiedStreamOption(
            language = displayLang,
            quality = quality.uppercase(),
            url = stream.url,
            providerId = stream.providerId,
            headers = stream.headers,
        )
    }
        .distinctBy { "${it.language}_${it.quality}" }
        .sortedWith(
            compareByDescending<UnifiedStreamOption> { QUALITY_ORDER[it.quality] ?: 0 }
                .thenBy { it.language },
        )
}

private val QUALITY_ORDER = mapOf(
    "UHD" to 7, "4K" to 6, "FHD" to 5, "HD" to 4, "SD" to 3, "HQ" to 2, "LQ" to 1,
)
