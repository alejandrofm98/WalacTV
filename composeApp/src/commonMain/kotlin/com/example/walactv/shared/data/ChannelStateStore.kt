package com.example.walactv.shared.data

import com.example.walactv.shared.domain.CatalogItem
import com.example.walactv.shared.domain.ContentKind
import com.russhwolf.settings.Settings

class ChannelStateStore(private val settings: Settings) {

    fun markRecent(item: CatalogItem) {
        if (item.kind != ContentKind.CHANNEL) return

        val updated = buildList {
            add(item.stableId)
            addAll(recentIds().filterNot { it == item.stableId })
        }.take(MAX_RECENTS)

        settings.putString(KEY_RECENTS_ORDER, updated.joinToString(SEPARATOR))
    }

    fun recentIds(): List<String> {
        val ordered = settings.getString(KEY_RECENTS_ORDER, "")
        if (ordered.isBlank()) return emptyList()
        return ordered.split(SEPARATOR).filter { it.isNotBlank() }
    }

    fun toggleFavorite(item: CatalogItem): Boolean {
        if (item.kind != ContentKind.CHANNEL) return false

        val current = favoriteIds().toMutableSet()
        val isFavorite = if (current.contains(item.stableId)) {
            current.remove(item.stableId)
            false
        } else {
            current.add(item.stableId)
            true
        }

        settings.putString(KEY_FAVORITES, current.joinToString(SEPARATOR))
        return isFavorite
    }

    fun setFavorite(item: CatalogItem, isFavorite: Boolean): Boolean {
        if (item.kind != ContentKind.CHANNEL) return false

        val current = favoriteIds().toMutableSet()
        if (isFavorite) {
            current.add(item.stableId)
        } else {
            current.remove(item.stableId)
        }

        settings.putString(KEY_FAVORITES, current.joinToString(SEPARATOR))
        return isFavorite
    }

    fun replaceFavoriteIds(ids: Collection<String>) {
        settings.putString(KEY_FAVORITES, ids.filter(String::isNotBlank).joinToString(SEPARATOR))
    }

    fun favoriteIds(): Set<String> {
        val raw = settings.getString(KEY_FAVORITES, "")
        if (raw.isBlank()) return emptySet()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }.toSet()
    }

    fun isFavorite(item: CatalogItem): Boolean {
        return item.kind == ContentKind.CHANNEL && favoriteIds().contains(item.stableId)
    }

    companion object {
        private const val KEY_RECENTS_ORDER = "recent_ids_order"
        private const val KEY_FAVORITES = "favorite_ids"
        private const val SEPARATOR = "|"
        private const val MAX_RECENTS = 12
    }
}
