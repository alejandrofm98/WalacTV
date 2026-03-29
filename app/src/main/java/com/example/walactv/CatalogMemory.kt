package com.example.walactv

object CatalogMemory {
    @Volatile
    var searchableItems: List<CatalogItem> = emptyList()

    @Volatile
    var channelRegistry: Map<String, CatalogItem> = emptyMap()

    fun registerChannel(item: CatalogItem) {
        channelRegistry = channelRegistry + (item.stableId to item)
    }
}
