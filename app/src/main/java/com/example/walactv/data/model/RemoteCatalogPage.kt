package com.example.walactv.data.model

data class RemoteCatalogPage(
    val items: List<CatalogItem>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val pages: Int,
    val hasNext: Boolean,
    val hasPrev: Boolean,
)
