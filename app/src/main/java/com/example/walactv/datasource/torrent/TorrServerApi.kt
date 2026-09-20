package com.example.walactv.datasource.torrent

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Fichero dentro de un torrent según TorrServer (ids productos del servidor). */
data class TorrServerFile(
    val id: Int,
    val path: String,
    val length: Long,
)

/** Estadisticas de un torrent activo en TorrServer. */
data class TorrServerStats(
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val peers: Int,
    val seeds: Int,
    val preloadedBytes: Long,
    val loadedSize: Long,
    val torrentSize: Long,
    val files: List<TorrServerFile>,
)

/**
 * Cliente HTTP del protocolo de TorrServer (proceso local en 127.0.0.1).
 * Implementacion propia sobre los endpoints documentados del servidor:
 * POST /torrents (add/get/drop) y GET /stream.
 */
class TorrServerApi(private val baseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Añade un magnet. Devuelve el hash tal como lo ve el servidor. */
    suspend fun addTorrent(magnetLink: String, title: String? = null): String? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("action", "add")
            put("link", magnetLink)
            put("save_to_db", false)
            if (title != null) put("title", title)
        }
        postJson("$baseUrl/torrents", body)?.optString("hash", "").orEmpty().ifBlank { null }
    }

    /** Estadisticas + lista de ficheros (null si el torrent no existe). */
    suspend fun getTorrentStats(hash: String): TorrServerStats? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("action", "get")
            put("hash", hash)
        }
        val json = postJson("$baseUrl/torrents", body) ?: return@withContext null
        val files = mutableListOf<TorrServerFile>()
        val fileList = json.optJSONArray("file_stats") ?: JSONArray()
        for (i in 0 until fileList.length()) {
            val f = fileList.optJSONObject(i) ?: continue
            files += TorrServerFile(
                id = f.optInt("id", i + 1),
                path = f.optString("path", ""),
                length = f.optLong("length", 0),
            )
        }
        TorrServerStats(
            downloadSpeed = json.optLong("download_speed", 0),
            uploadSpeed = json.optLong("upload_speed", 0),
            peers = json.optInt("active_peers", 0),
            seeds = json.optInt("connected_seeders", 0),
            preloadedBytes = json.optLong("preloaded_bytes", 0),
            loadedSize = json.optLong("loaded_size", 0),
            torrentSize = json.optLong("torrent_size", 0),
            files = files,
        )
    }

    /** Suelta un torrent del servidor (libera RAM/disco). */
    suspend fun dropTorrent(hash: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("action", "drop")
            put("hash", hash)
        }
        try {
            postJson("$baseUrl/torrents", body)
        } catch (e: Exception) {
            Log.w(TAG, "dropTorrent: ${e.message}")
        }
    }

    /**
     * URL HTTP reproducible de un fichero. El servidor la sirve con rangos;
     * ExoPlayer puede preparar y buscar sobre ella directamente.
     */
    fun streamUrl(magnetLink: String, fileId: Int): String {
        val encoded = URLEncoder.encode(magnetLink, "UTF-8")
        return "$baseUrl/stream?link=$encoded&index=$fileId&play"
    }

    private fun postJson(url: String, body: JSONObject): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_TYPE))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "POST $url: HTTP ${response.code}")
                    return null
                }
                JSONObject(response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            Log.w(TAG, "POST $url: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "TorrServerApi"
        private val JSON_TYPE = "application/json".toMediaType()
    }
}
