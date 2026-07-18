package com.example.walactv.data.remote.api.dto

import com.google.gson.annotations.SerializedName

data class SeriesEpisodesResponse(
    @SerializedName("items") val items: List<CatalogItemDto> = emptyList(),
    @SerializedName("episodes") val episodes: List<CatalogItemDto> = emptyList(),
    @SerializedName("total_episodes") val totalEpisodes: Int = 0,
    @SerializedName("seasons") val seasons: List<Int> = emptyList(),
    @SerializedName("serie_name") val serieName: String = "",
)
