package com.example.walactv.data.remote.api.dto

import com.google.gson.annotations.SerializedName

data class PlaybackPreferenceDto(
    @SerializedName("audio_language") val audioLanguage: String? = null,
    @SerializedName("audio_label") val audioLabel: String? = null,
    @SerializedName("subtitle_language") val subtitleLanguage: String? = null,
    @SerializedName("subtitle_label") val subtitleLabel: String? = null,
    @SerializedName("subtitles_disabled") val subtitlesDisabled: Boolean? = null,
)

data class PlaybackPreferenceUpdateBody(
    @SerializedName("audio_language") val audioLanguage: String? = null,
    @SerializedName("audio_label") val audioLabel: String? = null,
    @SerializedName("subtitle_language") val subtitleLanguage: String? = null,
    @SerializedName("subtitle_label") val subtitleLabel: String? = null,
    @SerializedName("subtitles_disabled") val subtitlesDisabled: Boolean? = null,
)
