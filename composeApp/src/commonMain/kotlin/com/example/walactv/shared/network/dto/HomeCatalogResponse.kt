package com.example.walactv.shared.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HomeCatalogResponse(
    val sections: List<SectionDto> = emptyList(),
    @SerialName("movie_sections") val movieSections: List<SectionDto> = emptyList(),
    @SerialName("series_sections") val seriesSections: List<SectionDto> = emptyList(),
)

@Serializable
data class SectionDto(
    val title: String? = null,
    val items: List<CatalogItemDto> = emptyList(),
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("group_name") val groupName: String? = null,
    @SerialName("section_title") val sectionTitle: String? = null,
    val year: Int? = null,
    @SerialName("has_next") val hasNext: Boolean = false,
)
