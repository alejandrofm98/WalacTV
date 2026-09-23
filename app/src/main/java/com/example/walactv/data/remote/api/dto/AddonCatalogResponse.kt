package com.example.walactv.data.remote.api.dto

data class AddonCatalogResponse(
    val items: List<CatalogItemDto> = emptyList(),
    val content_type: String? = null,
    val catalog_id: String? = null,
    val skip: Int = 0,
    val has_next: Boolean = false,
)
