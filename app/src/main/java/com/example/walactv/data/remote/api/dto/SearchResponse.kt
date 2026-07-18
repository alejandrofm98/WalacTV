package com.example.walactv.data.remote.api.dto

data class SearchResponse(
    val items: List<CatalogItemDto> = emptyList(),
    val types: List<String> = emptyList(),
) : PagedResponse()
