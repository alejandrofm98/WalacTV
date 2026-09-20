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
)
