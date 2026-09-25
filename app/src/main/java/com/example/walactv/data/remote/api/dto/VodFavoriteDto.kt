package com.example.walactv.data.remote.api.dto

import com.google.gson.annotations.SerializedName

data class VodFavoritesResponse(
    val items: List<VodFavoriteItemDto> = emptyList(),
    val total: Int = 0,
)

data class VodFavoriteItemDto(
    @SerializedName("content_type") val contentType: String,
    @SerializedName("content_id") val contentId: String,
    val item: CatalogItemDto? = null,
)

data class VodFavoriteBody(
    @SerializedName("content_type") val contentType: String,
    @SerializedName("content_id") val contentId: String,
)

data class VodFavoriteMutationResponse(
    val created: Boolean? = null,
    val deleted: Boolean? = null,
    @SerializedName("content_type") val contentType: String? = null,
    @SerializedName("content_id") val contentId: String? = null,
)
