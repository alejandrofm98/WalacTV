package com.example.walactv.anime

import com.google.gson.annotations.SerializedName

// ── API wrapper ─────────────────────────────────────────────────────────────

data class AnimeFlvResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T,
)

// ── List: en emisión ────────────────────────────────────────────────────────

data class AnimeOnAir(
    @SerializedName("title") val title: String,
    @SerializedName("type") val type: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("url") val url: String,
)

// ── List: últimos episodios ──────────────────────────────────────────────────

data class LatestEpisode(
    @SerializedName("title") val title: String,
    @SerializedName("number") val number: Int,
    @SerializedName("cover") val cover: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("url") val url: String,
)

// ── Search ──────────────────────────────────────────────────────────────────

data class SearchResult(
    @SerializedName("currentPage") val currentPage: Int,
    @SerializedName("hasNextPage") val hasNextPage: Boolean,
    @SerializedName("previousPage") val previousPage: String?,
    @SerializedName("nextPage") val nextPage: String?,
    @SerializedName("foundPages") val foundPages: Int,
    @SerializedName("media") val media: List<AnimeMedia>,
)

data class AnimeMedia(
    @SerializedName("title") val title: String,
    @SerializedName("cover") val cover: String,
    @SerializedName("synopsis") val synopsis: String,
    @SerializedName("rating") val rating: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("type") val type: String,
    @SerializedName("url") val url: String,
)

// ── Anime detail ────────────────────────────────────────────────────────────

data class AnimeDetail(
    @SerializedName("title") val title: String,
    @SerializedName("alternative_titles") val alternativeTitles: List<String>?,
    @SerializedName("status") val status: String,
    @SerializedName("rating") val rating: String,
    @SerializedName("type") val type: String,
    @SerializedName("cover") val cover: String,
    @SerializedName("synopsis") val synopsis: String,
    @SerializedName("genres") val genres: List<String>,
    @SerializedName("next_airing_episode") val nextAiringEpisode: String?,
    @SerializedName("episodes") val episodes: List<AnimeEpisodeEntry>,
    @SerializedName("url") val url: String,
    @SerializedName("related") val related: List<AnimeRelated>?,
)

data class AnimeEpisodeEntry(
    @SerializedName("number") val number: Int,
    @SerializedName("slug") val slug: String,
    @SerializedName("url") val url: String,
)

data class AnimeRelated(
    @SerializedName("title") val title: String,
    @SerializedName("relation") val relation: String?,
    @SerializedName("slug") val slug: String,
    @SerializedName("url") val url: String,
)

// ── Episode servers ─────────────────────────────────────────────────────────

data class EpisodeDetail(
    @SerializedName("title") val title: String,
    @SerializedName("number") val number: Int,
    @SerializedName("servers") val servers: List<AnimeServer>,
)

data class AnimeServer(
    @SerializedName("name") val name: String,
    @SerializedName("download") val download: String?,
    @SerializedName("embed") val embed: String?,
)
