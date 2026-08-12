package com.example.walactv.data.remote.repository

import android.util.Log
import com.example.walactv.data.remote.api.IptvApiService
import com.example.walactv.data.remote.api.dto.SaveWatchProgressBody
import com.example.walactv.data.remote.api.dto.WatchProgressDto
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class WatchProgressRepository @Inject constructor(private val apiService: IptvApiService) {

    suspend fun getContinueWatching(): Result<List<WatchProgressDto>> {
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
                Result.success(items)
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

    suspend fun getProgress(contentId: String): WatchProgressDto? {
        return try {
            val normalizedId = contentId.substringAfterLast(":")
            val response = apiService.getWatchProgressItem(normalizedId)
            if (response.isSuccessful) {
                response.body() ?: findProgressInList(contentId, normalizedId)
            } else {
                findProgressInList(contentId, normalizedId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Direct progress lookup failed for $contentId: ${e.message}")
            findProgressInList(contentId, contentId.substringAfterLast(":"))
        }
    }

    private suspend fun findProgressInList(contentId: String, normalizedId: String): WatchProgressDto? {
        val items = getContinueWatching().getOrDefault(emptyList())
        return items.firstOrNull { progress ->
            val progressId = progress.contentId.orEmpty()
            progressId == contentId || progressId == normalizedId ||
                progressId.substringAfterLast(":") == normalizedId ||
                progress.providerId == normalizedId
        }
    }

    suspend fun getWatchedItems(): Result<List<WatchProgressDto>> {
        return try {
            val all = mutableListOf<WatchProgressDto>()
            var offset = 0
            while (offset < MAX_WATCHED_ITEMS) {
                val response = apiService.getWatchedItems(limit = WATCHED_PAGE_SIZE, offset = offset)
                if (!response.isSuccessful) {
                    Log.e(TAG, "Error fetching watched items: ${response.code()}")
                    return Result.failure(Exception("Error fetching watched items: ${response.code()}"))
                }
                val body = response.body()
                val items = body?.items ?: emptyList()
                all += items
                val total = body?.total ?: (offset + items.size)
                offset += items.size
                if (items.isEmpty() || offset >= total) break
            }
            Log.d(TAG, "getWatchedItems: fetched ${all.size} watched items")
            Result.success(all)
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
    ): Result<WatchProgressDto> {
        return try {
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
                val saved = response.body()
                if (saved != null) {
                    Log.d(TAG, "saveProgress: SUCCESS $contentId at ${positionMs}ms")
                    Result.success(saved)
                } else {
                    Result.failure(Exception("Empty body saving progress for $contentId"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "saveProgress: FAILED $contentId code=${response.code()} error=$errorBody")
                Result.failure(Exception("Failed to save progress for $contentId: HTTP ${response.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "saveProgress: EXCEPTION $contentId", e)
            Result.failure(e)
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

    suspend fun markAsWatched(
        contentId: String,
        season: Int? = null,
        episode: Int? = null,
        completed: Boolean = false,
    ): Boolean {
        return try {
            val response = apiService.markWatched(contentId, season, episode, completed)
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Error marking as watched: $contentId", e)
            false
        }
    }

    companion object {
        private const val TAG = "WatchProgressRepo"
        private const val WATCHED_PAGE_SIZE = 500
        private const val MAX_WATCHED_ITEMS = 10_000
    }
}
