package com.example.walactv

data class CatalogFilterOption(
    val value: String,
    val label: String,
)

data class CatalogFilters(
    val countries: List<CatalogFilterOption> = emptyList(),
    val groups: List<CatalogFilterOption> = emptyList(),
    val genres: List<CatalogFilterOption> = emptyList(),
)
