package com.example.walactv.network.dto

import com.google.gson.annotations.SerializedName

data class SearchResponse(
    val items: List<CatalogItemDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerializedName("page_size") val pageSize: Int = 50,
    val pages: Int = 0,
    @SerializedName("has_next") val hasNext: Boolean = false,
    @SerializedName("has_prev") val hasPrev: Boolean = false,
    val types: List<String> = emptyList(),
)
