package com.example.walactv.data.remote.api.dto

import com.google.gson.annotations.SerializedName

data class ReplayListResponse(
    @SerializedName("items") val items: List<ReplayDto> = emptyList(),
    @SerializedName("total") val total: Int? = null,
    @SerializedName("page") val page: Int? = null,
    @SerializedName("page_size") val pageSize: Int? = null,
)

data class ReplayDto(
    val slug: String? = null,
    val title: String? = null,
    @SerializedName("event_name") val eventName: String? = null,
    @SerializedName("event_type") val eventType: String? = null,
    @SerializedName("event_date") val eventDate: String? = null,
    @SerializedName("featured_image_url") val featuredImageUrl: String? = null,
    val description: String? = null,
    @SerializedName("match_card") val matchCard: List<ReplayMatchCardDto>? = null,
    @SerializedName("video_sources") val videoSources: List<ReplayVideoSourceDto>? = null,
)

data class ReplayMatchCardDto(
    val fighter1: String? = null,
    val fighter2: String? = null,
    @SerializedName("weight_class") val weightClass: String? = null,
)

data class ReplayVideoSourceDto(
    val label: String? = null,
    val url: String? = null,
    val index: Int = 0,
    @SerializedName("button_index") val buttonIndex: Int = 0,
)
