package com.example.walactv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ChannelCacheManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "ChannelCacheManager"
        private const val PREFS_NAME = "channel_cache_prefs"
        private const val KEY_LAST_KNOWN_TOTAL = "last_known_total"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val CACHE_FILE_NAME = "channels_cache.json"
    }

    private val cacheFile: File
        get() = File(context.cacheDir, CACHE_FILE_NAME)

    fun getLastKnownTotal(): Int = prefs.getInt(KEY_LAST_KNOWN_TOTAL, -1)

    fun getLastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)

    fun isCacheEmpty(): Boolean = !cacheFile.exists() || cacheFile.length() == 0L

    suspend fun getChannelTotalFromApi(token: String): Int = withContext(Dispatchers.IO) {
        try {
            val url = "${BuildConfig.IPTV_BASE_URL}/api/content/stats?content_type=channels"
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000

                val status = conn.responseCode
                if (status !in 200..299) {
                    Log.e(TAG, "Failed to get stats: HTTP $status")
                    return@withContext -1
                }

                val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(body)
                val channels = json.optJSONObject("channels")
                channels?.optInt("total", -1) ?: -1
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting channel total from API", e)
            -1
        }
    }

    suspend fun needsSync(token: String): Boolean {
        if (isCacheEmpty()) {
            Log.d(TAG, "needsSync: cache empty")
            return true
        }

        val apiTotal = getChannelTotalFromApi(token)
        if (apiTotal < 0) {
            Log.d(TAG, "needsSync: could not get API total, using cache")
            return false
        }

        val lastKnown = getLastKnownTotal()
        val needs = apiTotal != lastKnown
        Log.d(TAG, "needsSync: apiTotal=$apiTotal, lastKnown=$lastKnown, needsSync=$needs")
        return needs
    }

    suspend fun syncFromApi(token: String, onProgress: ((Int, Int) -> Unit)? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting sync from API")
            
            val url = "${BuildConfig.IPTV_BASE_URL}/api/content/channels/all"
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 60_000
                conn.readTimeout = 60_000

                val status = conn.responseCode
                if (status !in 200..299) {
                    val errorBody = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: "Unknown error"
                    Log.e(TAG, "Failed to sync: HTTP $status - $errorBody")
                    return@withContext Result.failure(Exception("HTTP $status: $errorBody"))
                }

                val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: JSONArray()
                val total = json.optInt("total", 0)

                Log.d(TAG, "Received $total channels from API")

                val result = saveCache(items)
                if (result.isFailure) {
                    return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Failed to save cache"))
                }

                prefs.edit()
                    .putInt(KEY_LAST_KNOWN_TOTAL, total)
                    .putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                    .apply()

                Log.d(TAG, "Sync completed: $total channels cached")
                Result.success(total)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing from API", e)
            Result.failure(e)
        }
    }

    private fun saveCache(items: JSONArray): Result<Unit> {
        return try {
            cacheFile.writeText(items.toString())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing cache file", e)
            Result.failure(e)
        }
    }

    suspend fun loadCachedChannels(): List<CachedChannel> = withContext(Dispatchers.IO) {
        try {
            if (isCacheEmpty()) {
                Log.d(TAG, "loadCachedChannels: cache is empty")
                return@withContext emptyList()
            }

            val content = cacheFile.readText()
            val items = JSONArray(content)
            val result = mutableListOf<CachedChannel>()

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                result.add(
                    CachedChannel(
                        id = item.optString("id", ""),
                        logo = item.optString("logo", ""),
                        providerId = item.optString("provider_id", ""),
                        country = item.optString("country", ""),
                        nombreNormalizado = item.optString("nombre_normalizado", ""),
                        grupoNormalizado = item.optString("grupo_normalizado", ""),
                        numero = item.optInt("numero", 0)
                    )
                )
            }

            Log.d(TAG, "Loaded ${result.size} channels from cache")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached channels", e)
            emptyList()
        }
    }

    fun searchChannels(channels: List<CachedChannel>, query: String): List<CachedChannel> {
        if (query.isBlank()) return channels
        val lowerQuery = query.lowercase()
        return channels.filter { channel ->
            channel.nombreNormalizado.lowercase().contains(lowerQuery) ||
            channel.id.contains(lowerQuery) ||
            channel.numero.toString().contains(lowerQuery)
        }
    }

    fun filterByGroup(channels: List<CachedChannel>, group: String, showFavorites: Boolean, favoriteIds: Set<String>): List<CachedChannel> {
        return channels.filter { channel ->
            val matchesGroup = when {
                showFavorites -> favoriteIds.contains(channel.id)
                group == "Todos" || group.isBlank() -> true
                else -> channel.grupoNormalizado.equals(group, ignoreCase = true)
            }
            matchesGroup
        }.sortedBy { it.numero }
    }

    data class CachedChannel(
        val id: String,
        val logo: String,
        val providerId: String,
        val country: String,
        val nombreNormalizado: String,
        val grupoNormalizado: String,
        val numero: Int
    )
}
