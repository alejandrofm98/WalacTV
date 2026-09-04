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
    val languages: List<String> = emptyList(),
    val quality: String? = null,
    val provider: String? = null,
    val providerVideoId: String? = null,
    val streamFormat: String? = null,
    // ── Torrent (Torrentio) ─────────────────────────────────────────
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val seeders: Int? = null,
    val sizeBytes: Long? = null,
    val torrentTitle: String? = null,
) : Serializable {
    val isTorrent: Boolean get() = !infoHash.isNullOrBlank()
}

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

/**
 * Devuelve solo los torrents cuyo idioma declarado incluye el idioma
 * preferido del usuario. Torrentio etiqueta cada release con banderas
 * (p.ej. dual "🇬🇧 / 🇪🇸" = [EN, ES]); un torrent sin el idioma del
 * usuario no se ofrece para reproduccion.
 */
fun List<StreamOption>.filterByPreferredLanguage(preferredLanguage: String?): List<StreamOption> {
    val target = normalizeLanguageCode(preferredLanguage)
    return filter { stream ->
        val candidates = stream.languages.ifEmpty { listOfNotNull(stream.language) }
        candidates.any { normalizeLanguageCode(it) == target }
    }
}

internal val STREAM_QUALITY_ORDER = mapOf(
    "UHD" to 7, "4K" to 6, "FHD" to 5, "HD" to 4, "SD" to 3, "HQ" to 2, "LQ" to 1,
)

private val TORRENT_QUALITY_ORDER = mapOf(
    "UHD" to 7, "4K" to 6, "2160P" to 6, "BLURAY" to 5, "FHD" to 5, "1080P" to 5,
    "HD" to 4, "720P" to 4, "SD" to 3, "480P" to 3, "HDTV" to 2, "HQ" to 2, "LQ" to 1,
)

/**
 * Ordena torrents para elegir la fuente por defecto: primero los mas sembrados
 * (fiabilidad de la reproduccion) y a igualdad de seeds, mejor calidad y mayor
 * tamano. Un 4K con 1 seed no debe ser el default frente a un 1080p con 66.
 */
internal fun List<StreamOption>.bestTorrentFirst(): List<StreamOption> =
    sortedWith(
        compareByDescending<StreamOption> { it.seeders ?: 0 }
            .thenByDescending { TORRENT_QUALITY_ORDER[it.quality?.trim()?.uppercase()] ?: 0 }
            .thenByDescending { it.sizeBytes ?: 0L },
    )

/**
 * Orden por defecto al reproducir SIN eleccion manual del usuario:
 * 1) enlaces directos del proveedor (ordenados por calidad/idioma)
 * 2) torrents ordenados por mas seeds y luego mejor calidad.
 * La eleccion manual (selector Audio y Calidad / Fuentes) no pasa por aqui.
 */
internal fun List<StreamOption>.sortedForPlayback(): List<StreamOption> =
    sortedWith(
        compareBy<StreamOption> { it.isTorrent }
            .thenByDescending { STREAM_QUALITY_ORDER[it.quality?.trim()?.uppercase()] ?: 0 }
            .thenBy { it.language.isNullOrBlank() }
            .thenBy { it.language ?: "" }
            .thenByDescending { it.seeders ?: 0 }
            .thenBy { it.label },
    )
