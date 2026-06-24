package com.example.walactv

import android.util.Log
import com.example.walactv.network.IptvApiService
import com.example.walactv.network.dto.SaveWatchProgressBody
import com.example.walactv.network.dto.WatchProgressDto
import javax.inject.Inject

class WatchProgressRepository @Inject constructor(private val apiService: IptvApiService) {

    suspend fun getContinueWatching(): Result<List<WatchProgressItem>> {
        return try {
            Log.d(TAG, "getContinueWatching: CALLING API GET /api/watch-progress?limit=20")
            val response = apiService.getWatchProgress(limit = 20)
            Log.d(TAG, "getContinueWatching: API RESPONSE code=${response.code()} isSuccessful=${response.isSuccessful}")
            if (response.isSuccessful) {
                val items = response.body()?.items ?: emptyList()
                Log.d(TAG, "getContinueWatching: returned ${items.size} items")
                items.forEachIndexed { i, dto ->
                    Log.d(TAG, "getContinueWatching: item[$i] contentId=${dto.contentId} contentType=${dto.contentType} title=${dto.title} position=${dto.positionMs} isWatched=${dto.isWatched}")
                }
                Result.success(items.map { it.toDomain() })
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "getContinueWatching: FAILED code=${response.code()} error=$errorBody")
                Result.failure(Exception("Error fetching continue watching: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getContinueWatching: EXCEPTION", e)
            Result.failure(e)
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

    suspend fun getWatchedItems(): Result<List<WatchProgressItem>> {
        return try {
            val response = apiService.getWatchedItems(limit = 200)
            if (response.isSuccessful) {
                Result.success(response.body()?.items?.map { it.toDomain() } ?: emptyList())
            } else {
                Log.e(TAG, "Error fetching watched items: ${response.code()}")
                Result.failure(Exception("Error fetching watched items: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching watched items", e)
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
            Log.d(TAG, "saveProgress: CALLING API PUT /api/watch-progress/$contentId body=$body")
            val response = apiService.saveWatchProgress(contentId, body)
            Log.d(TAG, "saveProgress: API RESPONSE code=${response.code()} isSuccessful=${response.isSuccessful}")
            if (response.isSuccessful) {
                Log.d(TAG, "saveProgress: SUCCESS $contentId at ${positionMs}ms")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "saveProgress: FAILED $contentId code=${response.code()} error=$errorBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveProgress: EXCEPTION $contentId", e)
        }
    }

    suspend fun deleteProgress(contentId: String) {
        try {
            Log.d(TAG, "deleteProgress: CALLING API DELETE /api/watch-progress/$contentId")
            val response = apiService.deleteWatchProgress(contentId)
            Log.d(TAG, "deleteProgress: API RESPONSE code=${response.code()} isSuccessful=${response.isSuccessful}")
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "deleteProgress: FAILED code=${response.code()} error=$errorBody")
            } else {
                Log.d(TAG, "deleteProgress: SUCCESS $contentId deleted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteProgress: EXCEPTION for $contentId", e)
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
