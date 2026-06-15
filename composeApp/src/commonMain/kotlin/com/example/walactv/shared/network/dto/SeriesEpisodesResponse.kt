package com.example.walactv.shared.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeriesEpisodesResponse(
    val items: List<CatalogItemDto> = emptyList(),
    val episodes: List<CatalogItemDto> = emptyList(),
    @SerialName("total_episodes") val totalEpisodes: Int = 0,
    val seasons: List<Int> = emptyList(),
    @SerialName("serie_name") val serieName: String = "",
)
