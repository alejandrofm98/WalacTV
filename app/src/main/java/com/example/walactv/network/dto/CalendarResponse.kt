package com.example.walactv.network.dto

import com.google.gson.annotations.SerializedName

data class CalendarResponse(
    val fecha: String? = null,
    @SerializedName("total_eventos") val totalEventos: Int = 0,
    val eventos: List<CalendarEventDto> = emptyList(),
)

data class CalendarEventDto(
    val id: String? = null,
    val fecha: String? = null,
    val hora: String? = null,
    val competicion: String? = null,
    @SerializedName("subtitulo_competicion") val subtituloCompeticion: String? = null,
    val categoria: String? = null,
    val equipos: String? = null,
    @SerializedName("imagen_evento") val imagenEvento: String? = null,
    @SerializedName("canales_original") val canalesOriginal: List<String> = emptyList(),
    @SerializedName("canales_resueltos") val canalesResueltos: List<CanalResueltoDto> = emptyList(),
)

data class CanalResueltoDto(
    @SerializedName("channel_id") val channelId: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    val quality: String? = null,
    val priority: Int = 0,
    @SerializedName("source_name") val sourceName: String? = null,
    val logo: String? = null,
    @SerializedName("stream_url") val streamUrl: String? = null,
    @SerializedName("content_type") val contentType: String? = null,
    @SerializedName("provider_id") val providerId: String? = null,
)
