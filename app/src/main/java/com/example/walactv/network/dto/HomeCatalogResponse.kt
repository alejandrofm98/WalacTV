package com.example.walactv.network.dto

import com.google.gson.annotations.SerializedName

data class HomeCatalogResponse(
    val sections: List<SectionDto> = emptyList(),
    @SerializedName("movie_sections") val movieSections: List<SectionDto> = emptyList(),
    @SerializedName("series_sections") val seriesSections: List<SectionDto> = emptyList(),
)

data class SectionDto(
    val title: String? = null,
    val items: List<CatalogItemDto> = emptyList(),
    @SerializedName("content_type") val contentType: String? = null,
    @SerializedName("group_name") val groupName: String? = null,
    @SerializedName("has_next") val hasNext: Boolean = false,
)
