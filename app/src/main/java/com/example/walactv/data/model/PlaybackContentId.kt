package com.example.walactv.data.model

/** Identifier shared by playback and watch-progress operations. */
fun CatalogItem.playbackContentId(): String = providerId ?: stableId
