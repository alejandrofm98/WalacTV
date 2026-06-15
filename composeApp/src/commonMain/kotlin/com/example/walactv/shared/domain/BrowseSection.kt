package com.example.walactv.shared.domain

data class BrowseSection(
    val title: String,
    val items: List<CatalogItem>,
    val contentType: String? = null,
    val groupName: String? = null,
    val year: Int? = null,
    val sectionTitle: String? = null,
    val currentPage: Int = 1,
    val hasNextPage: Boolean = true,
)

data class HomeCatalog(
    val sections: List<BrowseSection>,
    val searchableItems: List<CatalogItem>,
    val favoriteItems: List<CatalogItem>? = null,
)
