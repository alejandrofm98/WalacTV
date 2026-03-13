package com.example.walactv

object CatalogMemory {
    @Volatile
    var searchableItems: List<CatalogItem> = emptyList()
}
