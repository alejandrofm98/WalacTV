package com.example.walactv.data.remote.api.dto

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    val access_token: String? = null,
    val token: String? = null,
    val access: String? = null,
    @SerializedName("iptv_enabled") val iptvEnabled: Boolean = true,
)
