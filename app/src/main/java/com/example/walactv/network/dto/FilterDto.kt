package com.example.walactv.network.dto

data class FilterOptionsResponse(
    val countries: List<FilterOptionDto> = emptyList(),
    val groups: List<FilterOptionDto> = emptyList(),
)

data class FilterOptionDto(
    val value: String = "",
    val label: String = "",
)
