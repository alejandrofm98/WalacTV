package com.example.walactv.data.remote.api.dto

import com.google.gson.annotations.SerializedName

data class WatchProgressListResponse(
    val items: List<WatchProgressDto> = emptyList(),
    val total: Int = 0,
)

data class WatchProgressDto(
    @SerializedName("content_id") val contentId: String? = null,
    @SerializedName("content_type") val contentType: String? = null,
    @SerializedName("provider_id") val providerId: String? = null,
    @SerializedName("position_ms") val positionMs: Long? = null,
    @SerializedName("duration_ms") val durationMs: Long? = null,
    @SerializedName("normalized_title") val normalizedTitle: String? = null,
    val title: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("series_name") val seriesName: String? = null,
    @SerializedName("series_provider_id") val seriesProviderId: String? = null,
    @SerializedName("season_number") val seasonNumber: Int? = null,
    @SerializedName("episode_number") val episodeNumber: Int? = null,
    @SerializedName("imdb_id") val imdbId: String? = null,
    @SerializedName("last_watched_at") val lastWatchedAt: String? = null,
    @SerializedName("is_watched") val isWatched: Boolean? = null,
    val overview: String? = null,
    @SerializedName("overview_en") val overviewEn: String? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("vote_count") val voteCount: Int? = null,
    @SerializedName("runtime_minutes") val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    val tagline: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    val year: Int? = null,
    @SerializedName("tmdb_title") val tmdbTitle: String? = null,
    @SerializedName("total_seasons") val totalSeasons: Int? = null,
)

data class SaveWatchProgressBody(
    @SerializedName("content_type") val contentType: String,
    @SerializedName("position_ms") val positionMs: Long,
    @SerializedName("duration_ms") val durationMs: Long,
    val title: String = "",
    @SerializedName("image_url") val imageUrl: String = "",
    @SerializedName("series_name") val seriesName: String? = null,
    @SerializedName("season_number") val seasonNumber: Int? = null,
    @SerializedName("episode_number") val episodeNumber: Int? = null,
)
