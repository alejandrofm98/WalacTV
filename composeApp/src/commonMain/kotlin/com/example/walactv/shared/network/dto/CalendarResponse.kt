package com.example.walactv.shared.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CalendarResponse(
    val fecha: String? = null,
    @SerialName("total_eventos") val totalEventos: Int = 0,
    val eventos: List<CalendarEventDto> = emptyList(),
)

@Serializable
data class CalendarEventDto(
    val id: String? = null,
    val fecha: String? = null,
    val hora: String? = null,
    val competicion: String? = null,
    @SerialName("subtitulo_competicion") val subtituloCompeticion: String? = null,
    val categoria: String? = null,
    val equipos: String? = null,
    @SerialName("imagen_evento") val imagenEvento: String? = null,
    @SerialName("canales_original") val canalesOriginal: List<String> = emptyList(),
    @SerialName("canales_resueltos") val canalesResueltos: List<CanalResueltoDto> = emptyList(),
)

@Serializable
data class CanalResueltoDto(
    @SerialName("channel_id") val channelId: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val quality: String? = null,
    val priority: Int = 0,
    @SerialName("source_name") val sourceName: String? = null,
    val logo: String? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("provider_id") val providerId: String? = null,
)
