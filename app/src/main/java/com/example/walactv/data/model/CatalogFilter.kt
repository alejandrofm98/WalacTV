package com.example.walactv.data.model

import com.example.walactv.data.remote.api.dto.FilterOptionDto

data class CatalogFilters(
    val countries: List<FilterOptionDto> = emptyList(),
    val groups: List<FilterOptionDto> = emptyList(),
    val genres: List<FilterOptionDto> = emptyList(),
)
