package com.example.walactv.shared.data

import com.example.walactv.shared.domain.WatchProgressItem
import com.example.walactv.shared.domain.buildTmdbImageUrl
import com.example.walactv.shared.network.IptvApiClient
import com.example.walactv.shared.network.dto.SaveWatchProgressBody
import com.example.walactv.shared.network.dto.WatchProgressDto

class WatchProgressRepository(private val apiClient: IptvApiClient) {

    suspend fun getContinueWatching(): Result<List<WatchProgressItem>> {
        return try {
            val response = apiClient.getWatchProgress(limit = 20)
            Result.success(response.items.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProgress(contentId: String): WatchProgressItem? {
        return try {
            apiClient.getWatchProgressItem(contentId).toDomain()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getWatchedItems(): Result<List<WatchProgressItem>> {
        return try {
            val response = apiClient.getWatchedItems(limit = 200)
            Result.success(response.items.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveProgress(
        contentId: String,
        contentType: String,
        positionMs: Long,
        durationMs: Long,
        title: String = "",
        imageUrl: String = "",
        seriesName: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ) {
        try {
            val body = SaveWatchProgressBody(
                contentType = contentType,
                positionMs = positionMs,
                durationMs = durationMs,
                title = title,
                imageUrl = imageUrl,
                seriesName = seriesName,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
            )
            apiClient.saveWatchProgress(contentId, body)
        } catch (_: Exception) {
        }
    }

    suspend fun deleteProgress(contentId: String) {
        try {
            apiClient.deleteWatchProgress(contentId)
        } catch (_: Exception) {
        }
    }

    suspend fun markAsWatched(contentId: String): Boolean {
        return try {
            apiClient.markWatched(contentId)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun WatchProgressDto.toDomain(): WatchProgressItem {
        return WatchProgressItem(
            contentId = contentId.orEmpty(),
            contentType = contentType.orEmpty(),
            positionMs = positionMs ?: 0L,
            durationMs = durationMs ?: 0L,
            normalizedTitle = normalizedTitle.orEmpty(),
            title = title.orEmpty(),
            imageUrl = imageUrl.orEmpty(),
            seriesName = seriesName?.ifBlank { null },
            seriesProviderId = seriesProviderId?.ifBlank { null },
            seasonNumber = seasonNumber?.takeIf { it > 0 },
            episodeNumber = episodeNumber?.takeIf { it > 0 },
            lastWatchedAt = lastWatchedAt.orEmpty(),
            isWatched = isWatched ?: false,
            overview = overview?.ifBlank { null } ?: overviewEn?.ifBlank { null },
            overviewEn = overviewEn?.ifBlank { null },
            voteAverage = voteAverage?.toFloat(),
            voteCount = voteCount?.takeIf { it > 0 },
            runtimeMinutes = runtimeMinutes?.takeIf { it > 0 },
            genres = genres.orEmpty(),
            posterPath = posterPath?.ifBlank { null },
            backdropPath = backdropPath?.ifBlank { null },
            tagline = tagline?.ifBlank { null },
            releaseDate = releaseDate?.ifBlank { null },
            year = year?.takeIf { it > 0 },
            tmdbTitle = tmdbTitle?.ifBlank { null },
            totalSeasons = totalSeasons?.takeIf { it > 0 },
        )
    }
}
