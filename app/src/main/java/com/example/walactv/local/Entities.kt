package com.example.walactv.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.walactv.BuildConfig
import com.example.walactv.CatalogItem
import com.example.walactv.ContentKind
import com.example.walactv.StreamOption
import com.example.walactv.cleanQualityLabels
import com.example.walactv.extractQualityLabel

@Entity(tableName = "channels", indices = [Index(value = ["nombreNormalizado"])])
data class ChannelEntity(
    @PrimaryKey val id: String,
    val numero: Int?,
    val providerId: String,
    val logo: String,
    val countries: String,
    val nombreNormalizado: String,
    val grupoNormalizado: String
)

@Entity(tableName = "movies", indices = [Index(value = ["nombreNormalizado"])])
data class MovieEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val nombre: String,
    val logo: String,
    val countries: String,
    val nombreNormalizado: String,
    val grupoNormalizado: String
)

@Entity(tableName = "series", indices = [Index(value = ["serieName"])])
data class SeriesEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val logo: String,
    val countries: String,
    val temporada: Int,
    val episodio: Int,
    val serieName: String,
    val nombreNormalizado: String,
    val grupoNormalizado: String
)

fun String.parseCountryList(): List<String> {
    return split(",").map { it.trim() }.filter { it.isNotBlank() }
}

private fun buildChannelStreamUrl(providerId: String, username: String, password: String): String {
    return "${BuildConfig.IPTV_BASE_URL}/live/$username/$password/$providerId"
}

private fun buildVodStreamUrl(providerId: String, username: String, password: String): String {
    return "${BuildConfig.IPTV_BASE_URL}/movie/$username/$password/$providerId"
}

fun ChannelEntity.toCatalogItem(username: String, password: String): CatalogItem {
    val streamUrl = if (providerId.isNotBlank()) {
        buildChannelStreamUrl(providerId, username, password)
    } else ""
    val cleanTitle = cleanQualityLabels(nombreNormalizado)
    val quality = extractQualityLabel(nombreNormalizado)
    return CatalogItem(
        stableId = id,
        providerId = providerId,
        title = cleanTitle,
        subtitle = "",
        description = "",
        imageUrl = logo,
        kind = ContentKind.CHANNEL,
        group = grupoNormalizado,
        badgeText = "",
        channelNumber = numero,
        countries = countries.parseCountryList(),
        streamOptions = if (streamUrl.isNotBlank()) listOf(
            StreamOption(
                label = nombreNormalizado,
                url = streamUrl,
                providerId = providerId,
                quality = quality,
            )
        ) else emptyList()
    )
}


