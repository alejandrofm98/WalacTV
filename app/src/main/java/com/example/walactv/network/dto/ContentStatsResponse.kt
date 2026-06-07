package com.example.walactv.network.dto

import com.google.gson.annotations.SerializedName

data class ContentStatsResponse(
    @SerializedName("generated_at") val generatedAt: String? = null,
    @SerializedName("total_count") val totalCount: Int = 0,
)
