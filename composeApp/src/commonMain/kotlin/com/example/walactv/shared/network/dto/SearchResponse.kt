package com.example.walactv.shared.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val items: List<CatalogItemDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 50,
    val pages: Int = 0,
    @SerialName("has_next") val hasNext: Boolean = false,
    @SerialName("has_prev") val hasPrev: Boolean = false,
    val types: List<String> = emptyList(),
)
