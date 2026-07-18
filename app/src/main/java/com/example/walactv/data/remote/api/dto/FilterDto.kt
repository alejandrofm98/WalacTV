package com.example.walactv.data.remote.api.dto

data class FilterOptionsResponse(
    val countries: List<FilterOptionDto> = emptyList(),
    val groups: List<FilterOptionDto> = emptyList(),
)

data class FilterOptionDto(
    val value: String = "",
    val label: String = "",
)

data class GenresResponse(
    val genres: List<String> = emptyList(),
)
