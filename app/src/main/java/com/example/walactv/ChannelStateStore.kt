package com.example.walactv

import android.content.Context

class ChannelStateStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun markRecent(item: CatalogItem) {
        if (item.kind != ContentKind.CHANNEL) return

        val updated = buildList {
            add(item.stableId)
            addAll(recentIds().filterNot { it == item.stableId })
        }.take(MAX_RECENTS)

        preferences.edit().putStringSet(KEY_RECENTS, updated.toSet()).apply()
        preferences.edit().putString(KEY_RECENTS_ORDER, updated.joinToString(SEPARATOR)).apply()
    }

    fun recentIds(): List<String> {
        val ordered = preferences.getString(KEY_RECENTS_ORDER, "").orEmpty()
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

        preferences.edit().putStringSet(KEY_FAVORITES, current).apply()
        return isFavorite
    }

    fun favoriteIds(): Set<String> {
        return preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
    }

    fun isFavorite(item: CatalogItem): Boolean {
        return item.kind == ContentKind.CHANNEL && favoriteIds().contains(item.stableId)
    }

    companion object {
        private const val PREFERENCES_NAME = "channel_state_store"
        private const val KEY_RECENTS = "recent_ids"
        private const val KEY_RECENTS_ORDER = "recent_ids_order"
        private const val KEY_FAVORITES = "favorite_ids"
        private const val SEPARATOR = "|"
        private const val MAX_RECENTS = 12
    }
}
