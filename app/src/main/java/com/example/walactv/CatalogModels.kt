package com.example.walactv

enum class ContentKind {
    EVENT,
    CHANNEL,
    MOVIE,
    SERIES,
}

data class StreamOption(
    val label: String,
    val url: String,
)

data class CatalogItem(
    val stableId: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val imageUrl: String,
    val kind: ContentKind,
    val group: String,
    val badgeText: String,
    val channelNumber: Int? = null,
    val streamOptions: List<StreamOption>,
)

fun CatalogItem.searchableText(): List<String> {
    return buildList {
        add(title)
        add(subtitle)
        add(description)
        add(group)
        add(kind.name)
        channelNumber?.let { add(it.toString()) }
    }
}

data class BrowseSection(
    val title: String,
    val items: List<CatalogItem>,
)

data class HomeCatalog(
    val sections: List<BrowseSection>,
    val searchableItems: List<CatalogItem>,
)
