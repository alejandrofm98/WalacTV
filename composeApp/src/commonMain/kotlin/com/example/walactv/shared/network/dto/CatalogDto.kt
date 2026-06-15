package com.example.walactv.shared.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CatalogPageResponse(
    val items: List<CatalogItemDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 24,
    val pages: Int = 0,
    @SerialName("has_next") val hasNext: Boolean = false,
    @SerialName("has_prev") val hasPrev: Boolean = false,
)

@Serializable
data class CatalogItemDto(
    val id: String? = null,
    @SerialName("provider_id") val providerId: String? = null,
    val title: String? = null,
    val type: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val group: String? = null,
    val description: String? = null,
    val overview: String? = null,
    @SerialName("overview_en") val overviewEn: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("tmdb_title") val tmdbTitle: String? = null,
    @SerialName("rating") val rating: Double? = null,
    val year: Int? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val genres: List<String>? = null,
    @SerialName("runtime_minutes") val runtimeMinutes: Int? = null,
    @SerialName("total_seasons") val totalSeasons: Int? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    @SerialName("episode_type") val episodeType: String? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("series_name") val seriesName: String? = null,
    @SerialName("series_key") val seriesKey: String? = null,
    @SerialName("stream_options") val streams: List<StreamDto>? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
    val countries: List<String>? = null,
    @SerialName("channel_number") val channelNumber: Int? = null,
    @SerialName("badge_text") val badgeText: String? = null,
    val subtitle: String? = null,
    val nombre: String? = null,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("channel_name") val channelName: String? = null,
    val grupo: String? = null,
    val logo: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    val image: String? = null,
    val poster: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    val backdrop: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    @SerialName("episode_id") val episodeId: String? = null,
    @SerialName("channel_id") val channelId: String? = null,
    val country: String? = null,
    val badge: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
)

typealias CatalogItemResponse = CatalogItemDto

@Serializable
data class StreamDto(
    val url: String? = null,
    val label: String? = null,
    val country: String? = null,
    val quality: String? = null,
    @SerialName("provider_id") val providerId: String? = null,
    val headers: Map<String, String>? = null,
)
