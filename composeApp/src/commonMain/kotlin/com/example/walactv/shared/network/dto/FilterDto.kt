package com.example.walactv.shared.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class FilterOptionsResponse(
    val countries: List<FilterOptionDto> = emptyList(),
    val groups: List<FilterOptionDto> = emptyList(),
)

@Serializable
data class FilterOptionDto(
    val value: String = "",
    val label: String = "",
)

@Serializable
data class GenresResponse(
    val genres: List<String> = emptyList(),
)
