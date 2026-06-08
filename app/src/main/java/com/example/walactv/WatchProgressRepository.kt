package com.example.walactv

import android.util.Log
import com.example.walactv.network.IptvApiService
import com.example.walactv.network.dto.SaveWatchProgressBody
import com.example.walactv.network.dto.WatchProgressDto
import javax.inject.Inject

class WatchProgressRepository @Inject constructor(private val apiService: IptvApiService) {

    suspend fun getContinueWatching(): List<WatchProgressItem> {
        return try {
            val response = apiService.getWatchProgress(limit = 20)
            if (response.isSuccessful) {
                response.body()?.items?.map { it.toDomain() } ?: emptyList()
            } else {
                Log.e(TAG, "Error fetching continue watching: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching continue watching", e)
            emptyList()
        }
    }

    suspend fun getProgress(contentId: String): WatchProgressItem? {
        return try {
            val response = apiService.getWatchProgressItem(contentId)
            if (response.isSuccessful) {
                response.body()?.toDomain()
            } else {
                Log.d(TAG, "No progress found for $contentId: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "No progress found for $contentId: ${e.message}")
            null
        }
    }

    suspend fun getWatchedItems(): List<WatchProgressItem> {
        return try {
            val response = apiService.getWatchedItems(limit = 200)
            if (response.isSuccessful) {
                response.body()?.items?.map { it.toDomain() } ?: emptyList()
            } else {
                Log.e(TAG, "Error fetching watched items: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching watched items", e)
            emptyList()
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
            val response = apiService.saveWatchProgress(contentId, body)
            if (response.isSuccessful) {
                Log.d(TAG, "Progress saved: $contentId at ${positionMs}ms")
            } else {
                Log.e(TAG, "Error saving progress for $contentId: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving progress for $contentId", e)
        }
    }

    suspend fun deleteProgress(contentId: String) {
        try {
            val response = apiService.deleteWatchProgress(contentId)
            if (!response.isSuccessful) {
                Log.e(TAG, "Error deleting progress for $contentId: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting progress for $contentId", e)
        }
    }

    suspend fun markAsWatched(contentId: String): Boolean {
        return try {
            val response = apiService.markWatched(contentId)
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Error marking as watched: $contentId", e)
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

    companion object {
        private const val TAG = "WatchProgressRepo"
    }
}
