package com.example.walactv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class WatchProgressRepository(context: Context) {

    private val credentialStore = CredentialStore(context.applicationContext)
    private var accessToken: String? = null

    suspend fun getContinueWatching(): List<WatchProgressItem> {
        return try {
            val token = getToken()
            val response = getJsonArray("${BuildConfig.IPTV_BASE_URL}/api/watch-progress?limit=20", token)
            val items = mutableListOf<WatchProgressItem>()
            for (i in 0 until response.length()) {
                val obj = response.getJSONObject(i)
                items.add(parseWatchProgressItem(obj))
            }
            items
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching continue watching", e)
            emptyList()
        }
    }

    suspend fun getProgress(contentId: String): WatchProgressItem? {
        return try {
            val token = getToken()
            val obj = getJsonObject("${BuildConfig.IPTV_BASE_URL}/api/watch-progress/$contentId", token)
            parseWatchProgressItem(obj)
        } catch (e: Exception) {
            Log.d(TAG, "No progress found for $contentId: ${e.message}")
            null
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
            val token = getToken()
            val body = JSONObject().apply {
                put("content_type", contentType)
                put("position_ms", positionMs)
                put("duration_ms", durationMs)
                put("title", title)
                put("image_url", imageUrl)
                seriesName?.let { put("series_name", it) }
                seasonNumber?.let { put("season_number", it) }
                episodeNumber?.let { put("episode_number", it) }
            }
            putJson("${BuildConfig.IPTV_BASE_URL}/api/watch-progress/$contentId", body, token)
            Log.d(TAG, "Progress saved: $contentId at ${positionMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving progress for $contentId", e)
        }
    }

    suspend fun deleteProgress(contentId: String) {
        try {
            val token = getToken()
            deleteRequest("${BuildConfig.IPTV_BASE_URL}/api/watch-progress/$contentId", token)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting progress for $contentId", e)
        }
    }

    suspend fun markAsWatched(contentId: String): Boolean {
        return try {
            val token = getToken()
            val body = JSONObject()
            postJson("${BuildConfig.IPTV_BASE_URL}/api/watch-progress/$contentId/mark-watched", body, token)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error marking as watched: $contentId", e)
            false
        }
    }

    suspend fun markAsUnwatched(contentId: String): Boolean {
        return try {
            val token = getToken()
            val body = JSONObject()
            postJson("${BuildConfig.IPTV_BASE_URL}/api/watch-progress/$contentId/mark-unwatched", body, token)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error marking as unwatched: $contentId", e)
            false
        }
    }

    // ── Token management ────────────────────────────────────────────────────

    private suspend fun getToken(): String {
        accessToken?.let { return it }
        val user = credentialStore.username()
        val pass = credentialStore.password()
        check(user.isNotBlank() && pass.isNotBlank()) { "No hay sesión iniciada" }

        val response = postForm(
            url = "${BuildConfig.IPTV_BASE_URL}/api/auth/login",
            body = "username=${encodeParam(user)}&password=${encodeParam(pass)}",
        )
        return response.getString("access_token").also { accessToken = it }
    }

    // ── HTTP helpers ────────────────────────────────────────────────────────

    private suspend fun getJsonObject(url: String, token: String): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $token")
                val body = readBody(conn)
                if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}: $body")
                JSONObject(body)
            } finally {
                conn.disconnect()
            }
        }

    private suspend fun getJsonArray(url: String, token: String): JSONArray =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $token")
                val body = readBody(conn)
                if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}: $body")
                val json = JSONObject(body)
                json.optJSONArray("items") ?: JSONArray()
            } finally {
                conn.disconnect()
            }
        }

    private suspend fun putJson(url: String, body: JSONObject, token: String): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "PUT"
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val responseBody = readBody(conn)
                if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}: $responseBody")
                JSONObject(responseBody)
            } finally {
                conn.disconnect()
            }
        }

    private suspend fun postJson(url: String, body: JSONObject, token: String): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val responseBody = readBody(conn)
                if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}: $responseBody")
                JSONObject(responseBody)
            } finally {
                conn.disconnect()
            }
        }

    private suspend fun postForm(url: String, body: String): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setRequestProperty("Accept", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
                val responseBody = readBody(conn)
                if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}: $responseBody")
                JSONObject(responseBody)
            } finally {
                conn.disconnect()
            }
        }

    private suspend fun deleteRequest(url: String, token: String) =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "DELETE"
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.responseCode // trigger request
            } finally {
                conn.disconnect()
            }
        }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
    }

    private fun encodeParam(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun parseWatchProgressItem(obj: JSONObject): WatchProgressItem {
        return WatchProgressItem(
            contentId = obj.getString("content_id"),
            contentType = obj.getString("content_type"),
            positionMs = obj.getLong("position_ms"),
            durationMs = obj.getLong("duration_ms"),
            normalizedTitle = obj.optString("normalized_title", ""),
            title = obj.optString("title", ""),
            imageUrl = obj.optString("image_url", ""),
            seriesName = obj.optString("series_name", "").ifBlank { null },
            seasonNumber = obj.optInt("season_number", 0).takeIf { it > 0 },
            episodeNumber = obj.optInt("episode_number", 0).takeIf { it > 0 },
            lastWatchedAt = obj.optString("last_watched_at", ""),
            isWatched = obj.optBoolean("is_watched", false),
        )
    }

    companion object {
        private const val TAG = "WatchProgressRepo"
    }
}
