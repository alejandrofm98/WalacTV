package com.example.walactv.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.walactv.BuildConfig
import com.example.walactv.CatalogItem
import com.example.walactv.ContentKind
import com.example.walactv.StreamOption

@Entity(tableName = "channels", indices = [Index(value = ["nombreNormalizado"])])
data class ChannelEntity(
    @PrimaryKey val id: String,
    val numero: Int?,
    val providerId: String,
    val logo: String,
    val country: String,
    val nombreNormalizado: String,
    val grupoNormalizado: String
)

@Entity(tableName = "movies", indices = [Index(value = ["nombreNormalizado"])])
data class MovieEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val nombre: String,
    val logo: String,
    val country: String,
    val nombreNormalizado: String,
    val grupoNormalizado: String
)

@Entity(tableName = "series", indices = [Index(value = ["serieName"])])
data class SeriesEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val logo: String,
    val country: String,
    val temporada: Int,
    val episodio: Int,
    val serieName: String,
    val nombreNormalizado: String,
    val grupoNormalizado: String
)

data class ChannelWithFavorite(
    val id: String,
    val numero: Int?,
    val providerId: String,
    val logo: String,
    val country: String,
    val nombreNormalizado: String,
    val grupoNormalizado: String,
    val isFavorite: Boolean
)

private fun buildChannelStreamUrl(providerId: String, username: String, password: String): String {
    return "${BuildConfig.IPTV_BASE_URL}/$username/$password/$providerId"
}

private fun buildVodStreamUrl(providerId: String, username: String, password: String): String {
    return "${BuildConfig.IPTV_BASE_URL}/movie/$username/$password/$providerId"
}

fun ChannelEntity.toCatalogItem(username: String, password: String): CatalogItem {
    val streamUrl = if (providerId.isNotBlank()) {
        buildChannelStreamUrl(providerId, username, password)
    } else ""
    return CatalogItem(
        stableId = id,
        providerId = providerId,
        title = nombreNormalizado,
        subtitle = "",
        description = "",
        imageUrl = logo,
        kind = ContentKind.CHANNEL,
        group = grupoNormalizado,
        badgeText = "",
        channelNumber = numero,
        streamOptions = if (streamUrl.isNotBlank()) listOf(StreamOption(label = "Ver", url = streamUrl, providerId = providerId)) else emptyList()
    )
}

fun MovieEntity.toCatalogItem(username: String, password: String): CatalogItem {
    val streamUrl = if (providerId.isNotBlank()) {
        buildVodStreamUrl(providerId, username, password)
    } else ""
    return CatalogItem(
        stableId = id,
        providerId = providerId,
        title = nombre,
        normalizedTitle = nombreNormalizado,
        subtitle = "",
        description = "",
        imageUrl = logo,
        kind = ContentKind.MOVIE,
        group = grupoNormalizado,
        badgeText = "",
        streamOptions = if (streamUrl.isNotBlank()) listOf(StreamOption(label = "Ver", url = streamUrl, providerId = providerId)) else emptyList()
    )
}

fun SeriesEntity.toCatalogItem(username: String, password: String): CatalogItem {
    val streamUrl = if (providerId.isNotBlank()) {
        buildVodStreamUrl(providerId, username, password)
    } else ""
    return CatalogItem(
        stableId = id,
        providerId = providerId,
        title = serieName,
        subtitle = if (temporada > 0 && episodio > 0) "T${temporada}E${episodio}" else grupoNormalizado,
        description = "",
        imageUrl = logo,
        kind = ContentKind.SERIES,
        group = grupoNormalizado,
        badgeText = "",
        seriesName = serieName,
        seasonNumber = temporada,
        episodeNumber = episodio,
        streamOptions = if (streamUrl.isNotBlank()) listOf(StreamOption(label = "Ver", url = streamUrl, providerId = providerId)) else emptyList()
    )
}
