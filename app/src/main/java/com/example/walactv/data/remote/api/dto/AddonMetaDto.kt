package com.example.walactv.data.remote.api.dto

import com.google.gson.annotations.SerializedName

data class AddonMetaDto(
    @SerializedName("imdb_id") val imdbId: String,
    @SerializedName("content_type") val contentType: String,
    val name: String? = null,
    @SerializedName("description_en") val descriptionEn: String? = null,
    @SerializedName("overview_es") val overviewEs: String? = null,
    @SerializedName("title_es") val titleEs: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val genres: List<String> = emptyList(),
    @SerializedName("imdb_rating") val imdbRating: String? = null,
    @SerializedName("moviedb_id") val movieDbId: Int? = null,
    val episodes: List<AddonEpisodeDto> = emptyList(),
)

data class AddonEpisodeDto(
    val id: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val title: String? = null,
    val overview: String? = null,
    @SerializedName("overview_es") val overviewEs: String? = null,
    @SerializedName("overview_en") val overviewEn: String? = null,
    val thumbnail: String? = null,
    val released: String? = null,
)
