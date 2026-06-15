package com.example.walactv.shared.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContentStatsResponse(
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("total_count") val totalCount: Int = 0,
)
