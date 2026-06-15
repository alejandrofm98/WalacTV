package com.example.walactv.shared.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WatchProgressListResponse(
    val items: List<WatchProgressDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class WatchProgressDto(
    @SerialName("content_id") val contentId: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("position_ms") val positionMs: Long? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("normalized_title") val normalizedTitle: String? = null,
    val title: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("series_name") val seriesName: String? = null,
    @SerialName("series_provider_id") val seriesProviderId: String? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("last_watched_at") val lastWatchedAt: String? = null,
    @SerialName("is_watched") val isWatched: Boolean? = null,
    val overview: String? = null,
    @SerialName("overview_en") val overviewEn: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("runtime_minutes") val runtimeMinutes: Int? = null,
    val genres: List<String>? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val tagline: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val year: Int? = null,
    @SerialName("tmdb_title") val tmdbTitle: String? = null,
    @SerialName("total_seasons") val totalSeasons: Int? = null,
)

@Serializable
data class SaveWatchProgressBody(
    @SerialName("content_type") val contentType: String,
    @SerialName("position_ms") val positionMs: Long,
    @SerialName("duration_ms") val durationMs: Long,
    val title: String = "",
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("series_name") val seriesName: String? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
)
