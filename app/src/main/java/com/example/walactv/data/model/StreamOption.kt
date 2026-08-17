package com.example.walactv.data.model

import com.example.walactv.data.util.languageDisplayLabel
import com.example.walactv.data.util.normalizeLanguageCode
import java.io.Serializable

private val KNOWN_QUALITY_TOKENS = setOf("UHD", "4K", "FHD", "HD", "SD", "HEVC", "H265", "HQ", "LQ")

data class StreamOption(
    val label: String,
    val url: String,
    val providerId: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val language: String? = null,
    val quality: String? = null,
    val provider: String? = null,
    val providerVideoId: String? = null,
    val streamFormat: String? = null,
) : Serializable

data class UnifiedStreamOption(
    val language: String,
    val languageCode: String,
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
            languageCode = normalizeLanguageCode(lang),
            quality = quality.uppercase(),
            url = stream.url,
            providerId = stream.providerId,
            headers = stream.headers,
        )
    }
        .distinctBy { it.url }
        .sortedWith(
            compareByDescending<UnifiedStreamOption> { STREAM_QUALITY_ORDER[it.quality] ?: 0 }
                .thenBy { it.language },
        )
}

internal val STREAM_QUALITY_ORDER = mapOf(
    "UHD" to 7, "4K" to 6, "FHD" to 5, "HD" to 4, "SD" to 3, "HQ" to 2, "LQ" to 1,
)

/**
 * Ordena las opciones de stream de VOD (peliculas/series) para elegir cual se
 * intenta reproducir al abrir el contenido: mejor calidad primero y opciones
 * con idioma antes que las que no tienen. Asi la primera coincide con la
 * primera opcion del selector unificado (que ordena por calidad), en lugar de
 * caer en la primera en orden del servidor (normalmente el "Directo" de
 * stream_url, que suele fallar). El selector sigue ordenando por su cuenta.
 */
internal fun List<StreamOption>.sortedForPlayback(): List<StreamOption> =
    sortedWith(
        compareByDescending<StreamOption> { STREAM_QUALITY_ORDER[it.quality?.trim()?.uppercase()] ?: 0 }
            .thenBy { it.language.isNullOrBlank() }
            .thenBy { it.language ?: "" }
            .thenBy { it.label },
    )
