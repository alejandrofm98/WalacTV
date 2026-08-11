package com.example.walactv.data.model

import com.google.gson.annotations.SerializedName

data class SkipSegment(
    @SerializedName("start_ms") val startMs: Long?,
    @SerializedName("end_ms") val endMs: Long?,
    val confidence: Double? = null,
    @SerializedName("submission_count") val submissionCount: Int? = null,
)

data class SkipSegments(
    val intro: SkipSegment? = null,
    val recap: SkipSegment? = null,
    val outro: SkipSegment? = null,
)
