package com.example.walactv

data class StreamOption(
    val label: String,
    val url: String,
    val providerId: String? = null,
    val headers: Map<String, String> = emptyMap(),
)
