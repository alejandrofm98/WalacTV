package com.example.walactv.shared.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    @SerialName("access_token") val accessToken: String? = null,
    val token: String? = null,
    val access: String? = null,
)
