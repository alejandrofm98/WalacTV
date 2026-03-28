package com.example.walactv

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class RemoteCatalogPage(
    val items: List<CatalogItem>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val pages: Int,
    val hasNext: Boolean,
    val hasPrev: Boolean,
)

fun resolveStreamTemplate(template: String, username: String, password: String): String {
    if (template.isBlank()) return ""
    return template
        .replace("{{USERNAME}}", username)
        .replace("{{PASSWORD}}", password)
}

fun parseRemoteHomeCatalog(payload: JSONObject): HomeCatalog {
    val sections = buildList {
        addSectionIfNotEmpty("Eventos", payload.optJSONArray("events"), expectedKind = ContentKind.EVENT)
        addSectionIfNotEmpty("Canales", payload.optJSONArray("featured_channels"), expectedKind = ContentKind.CHANNEL)
        addSectionIfNotEmpty("Peliculas", payload.optJSONArray("featured_movies"), expectedKind = ContentKind.MOVIE)
        addSeriesSectionIfNotEmpty("Series", payload.optJSONArray("featured_series"), expectedKind = ContentKind.SERIES)
    }
    val searchableItems = sections.flatMap(BrowseSection::items).distinctBy(CatalogItem::stableId)
    return HomeCatalog(sections = sections, searchableItems = searchableItems)
}

fun parseRemoteCatalogPage(payload: JSONObject, expectedKind: ContentKind? = null): RemoteCatalogPage {
    val items = payload.optJSONArray("items").toCatalogItems(expectedKind)
    val total = payload.optInt("total")
    val page = payload.optInt("page", 1)
    val pageSize = payload.optInt("page_size", 0)
    val pages = payload.optInt("pages", 0)
    val hasNext = when {
        payload.has("has_next") -> payload.optBoolean("has_next")
        pages > 0 -> page < pages
        total > 0 && pageSize > 0 -> page * pageSize < total
        else -> false
    }
    val hasPrev = when {
        payload.has("has_prev") -> payload.optBoolean("has_prev")
        pages > 0 -> page > 1
        total > 0 && pageSize > 0 -> page > 1
        else -> false
    }

    return RemoteCatalogPage(
        items = items,
        total = total,
        page = page,
        pageSize = pageSize,
        pages = pages,
        hasNext = hasNext,
        hasPrev = hasPrev,
    )
}

fun parseRemoteCatalogItem(payload: JSONObject, expectedKind: ContentKind? = null): CatalogItem {
    return payload.toCatalogItem(expectedKind)
}

fun parseRemoteFilterOptions(payload: JSONObject, key: String): List<CatalogFilterOption> {
    val values = payload.optJSONArray(key) ?: JSONArray()
    return buildList {
        for (index in 0 until values.length()) {
            val rawValue = values.opt(index)
            when (rawValue) {
                is JSONObject -> {
                    val value = rawValue.optString("code")
                        .ifBlank { rawValue.optString("value") }
                        .ifBlank { rawValue.optString("id") }
                        .trim()
                    val label = rawValue.optString("name")
                        .ifBlank { rawValue.optString("label") }
                        .ifBlank { rawValue.optString("display_name") }
                        .ifBlank { value }
                        .trim()
                    if (value.isNotBlank() && label.isNotBlank()) {
                        add(CatalogFilterOption(value = value, label = label))
                    }
                }

                else -> {
                    val value = values.optString(index).trim()
                    if (value.isNotBlank()) add(CatalogFilterOption(value = value, label = value))
                }
            }
        }
    }
        .distinctBy(CatalogFilterOption::value)
        .sortedBy(CatalogFilterOption::label)
}

fun buildRemoteCatalogFilters(
    kind: ContentKind,
    countriesPayload: JSONObject? = null,
    groupsPayload: JSONObject? = null,
): CatalogFilters {
    if (kind == ContentKind.EVENT) return CatalogFilters()
    return CatalogFilters(
        countries = parseRemoteFilterOptions(countriesPayload ?: JSONObject(), "countries"),
        groups = parseRemoteFilterOptions(groupsPayload ?: JSONObject(), "groups"),
    )
}

fun buildCatalogQuery(
    contentType: String,
    page: Int,
    country: String? = null,
    group: String? = null,
    search: String? = null,
): String {
    val countryParam = country?.takeIf { it.isNotBlank() }?.let { "&country=${URLEncoder.encode(it, Charsets.UTF_8.name())}" }.orEmpty()
    val groupParam = group?.takeIf { it.isNotBlank() }?.let { "&group=${URLEncoder.encode(it, Charsets.UTF_8.name())}" }.orEmpty()
    val searchParam = search?.takeIf { it.isNotBlank() }?.let { "&search=${URLEncoder.encode(it, Charsets.UTF_8.name())}" }.orEmpty()
    return "content_type=$contentType&page=$page&page_size=50$countryParam$groupParam$searchParam"
}

fun buildGroupsQuery(
    contentType: String,
    countries: String,
): String {
    return "content_type=$contentType&countries=${URLEncoder.encode(countries, Charsets.UTF_8.name())}"
}

private fun MutableList<BrowseSection>.addSectionIfNotEmpty(title: String, items: JSONArray?, expectedKind: ContentKind? = null) {
    val catalogItems = items.toCatalogItems(expectedKind)
    if (catalogItems.isNotEmpty()) {
        add(BrowseSection(title, catalogItems))
    }
}

private fun MutableList<BrowseSection>.addSeriesSectionIfNotEmpty(title: String, items: JSONArray?, expectedKind: ContentKind? = null) {
    val catalogItems = items.toCatalogItems(expectedKind)
        .groupBy { it.seriesName ?: it.title }
        .map { (seriesName, episodes) ->
            val firstEpisode = episodes.first()
            firstEpisode.copy(
                stableId = "series_group:$seriesName",
                title = seriesName,
                subtitle = firstEpisode.group,
                description = firstEpisode.description,
                streamOptions = emptyList(),
                seriesName = seriesName,
                seasonNumber = null,
                episodeNumber = null,
            )
        }
    if (catalogItems.isNotEmpty()) {
        add(BrowseSection(title, catalogItems))
    }
}

private fun JSONArray?.toCatalogItems(expectedKind: ContentKind? = null): List<CatalogItem> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            add(getJSONObject(index).toCatalogItem(expectedKind))
        }
    }
}

private fun JSONObject.toCatalogItem(expectedKind: ContentKind? = null): CatalogItem {
    val type = optCleanString("type")
        .ifBlank { optCleanString("content_type") }
        .ifBlank { optCleanString("media_type") }
        .trim()
        .lowercase()
    val kind = when {
        type.isBlank() && expectedKind != null -> expectedKind
        else -> when (type) {
        "channel", "channels", "live" -> ContentKind.CHANNEL
        "event" -> ContentKind.EVENT
        "movie", "movies", "vod" -> ContentKind.MOVIE
        "series", "serie" -> ContentKind.SERIES
        else -> ContentKind.CHANNEL
        }
    }
    val rawId = optCleanString("id").ifBlank { optCleanString("channel_id") }
    val providerId = optCleanString("provider_id").ifBlank { null }
    val stableIdValue = providerId ?: rawId
    val stableId = if (kind == ContentKind.EVENT) stableIdValue else "${kind.name.lowercase()}:$stableIdValue"
    val streamUrl = optCleanString("stream_url")

    val rawTitle = optCleanString("nombre").ifBlank {
        optCleanString("title").ifBlank {
            optCleanString("name").ifBlank {
                optCleanString("display_name").ifBlank {
                    optCleanString("channel_name")
                }
            }
        }
    }
    val nombreNormalizado = optCleanString("nombre_normalizado")
        .ifBlank { optCleanString("normalized_title") }

    val inferredChannelNumber = Regex("^\\s*(\\d{1,5})\\s+")
        .find(rawTitle)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    val titleWithoutChannelNumber = rawTitle.replace(Regex("^\\s*\\d{1,5}\\s+"), "").trim()
    val channelDisplayName = optCleanString("display_name")
        .ifBlank { optCleanString("channel_name") }

    val rawGroup = optCleanString("grupo").ifBlank {
        optCleanString("group").ifBlank {
            optCleanString("subtitle")
        }
    }
    val grupoNormalizado = optCleanString("grupo_normalizado")
        .ifBlank { optCleanString("normalized_group") }

    val normalized = parseNormalizedMetadata(
        kind = kind,
        groupTitle = rawGroup,
        tvgName = titleWithoutChannelNumber,
        displayName = titleWithoutChannelNumber,
        walacLanguage = optCleanString("country"),
        walacNameNormalized = nombreNormalizado,
        walacGroupNormalized = grupoNormalizado,
        walacSeriesNameNormalized = optCleanString("series_name"),
    )
    val description = optCleanString("description").ifBlank { optCleanString("subtitle") }

    return CatalogItem(
        stableId = stableId,
        providerId = providerId,
        title = buildRemoteDisplayTitle(kind, normalized.displayTitle, channelDisplayName),
        normalizedTitle = nombreNormalizado.ifBlank { null },
        subtitle = normalized.groupTitle.ifBlank { rawGroup },
        description = description.ifBlank { normalized.groupTitle.ifBlank { rawGroup } },
        imageUrl = normalizeRemoteImageUrl(
            optCleanString("logo").ifBlank {
                optCleanString("image_url").ifBlank {
                    optCleanString("logo_url").ifBlank {
                        optCleanString("stream_icon").ifBlank {
                            optCleanString("poster")
                        }
                    }
                }
            }
        ),
        kind = kind,
        group = normalized.groupTitle.ifBlank { rawGroup },
        badgeText = optCleanString("badge_text").ifBlank { optCleanString("quality") },
        channelNumber = optInt("num").takeIf { has("num") } ?: inferredChannelNumber,
        languageLabel = normalized.languageLabel?.takeIf { it.isNotBlank() },
        normalizedGroup = grupoNormalizado.ifBlank { null },
        seriesName = normalized.seriesName?.takeIf { it.isNotBlank() },
        seasonNumber = optCleanString("temporada").toIntOrNull()
            ?: optInt("season_number").takeIf { has("season_number") },
        episodeNumber = optCleanString("episodio").toIntOrNull()
            ?: optInt("episode_number").takeIf { has("episode_number") },
        streamOptions = listOfNotNull(
            streamUrl.takeIf { it.isNotBlank() }?.let {
                StreamOption(
                    label = optCleanString("stream_label").ifBlank { defaultStreamLabel(kind) },
                    url = it,
                )
            },
        ),
    )
}

private fun JSONObject.optCleanString(key: String): String {
    return optString(key)
        .takeUnless { it.equals("null", ignoreCase = true) }
        ?.trim()
        .orEmpty()
}

private fun buildRemoteDisplayTitle(kind: ContentKind, normalizedTitle: String, channelDisplayName: String): String {
    if (kind != ContentKind.CHANNEL) return normalizedTitle
    val display = channelDisplayName.replace(Regex("^\\s*\\d{1,5}\\s+"), "").trim()
    return display.ifBlank { normalizedTitle }
}

private fun defaultStreamLabel(kind: ContentKind): String {
    return when (kind) {
        ContentKind.CHANNEL,
        ContentKind.EVENT,
        -> "Directo"
        ContentKind.MOVIE -> "Reproducir"
        ContentKind.SERIES -> "Episodio"
    }
}

private fun normalizeRemoteImageUrl(url: String): String {
    if (url.isBlank() || url == "null") return ""
    return url
        .replace("http://${BuildConfig.IPTV_BASE_URL.removePrefix("https://").removePrefix("http://")}", BuildConfig.IPTV_BASE_URL)
        .replace("http://image.tmdb.org", "https://image.tmdb.org")
}
