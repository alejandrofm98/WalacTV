package com.example.walactv.anime

import android.util.Log
import com.example.walactv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object VideoExtractorApiClient {

    private const val TAG = "VideoExtractorApi"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String
        get() = BuildConfig.IPTV_BASE_URL

    /**
     * Token JWT para autenticación. Se establece desde ComposeMainFragment
     * cuando el usuario hace login.
     */
    var authToken: String? = null

    /**
     * Extrae la URL directa de reproducción a partir de una URL de embed.
     *
     * Proveedores soportados: streamtape, stape, netu, streamhide, vidhide,
     * streamwish, filelions, vidmoly, doodstream, filemoon, mp4upload,
     * okru, uqload, upstream, voe, lulustream.
     *
     * @param embedUrl URL de embed del hosting
     * @return Result con la URL directa del video o error
     */
    suspend fun extract(embedUrl: String): Result<VideoExtractResult> {
        return withContext(Dispatchers.IO) {
            try {
                val token = authToken
                if (token.isNullOrBlank()) {
                    Log.w(TAG, "No auth token available")
                    return@withContext Result.failure(Exception("No hay token de autenticación"))
                }

                val encodedUrl = java.net.URLEncoder.encode(embedUrl, "UTF-8")
                val url = "${baseUrl}/api/video-extract?url=$encodedUrl"

                Log.d(TAG, "Extracting: $embedUrl")

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val success = json.optBoolean("success", false)
                    if (!success) {
                        val error = json.optString("error", "Error desconocido")
                        return@withContext Result.failure(Exception(error))
                    }

                    val directUrl = json.optString("url", "")
                    val provider = json.optString("provider", "unknown")
                    val type = json.optString("type", "mp4")

                    // Extraer headers requeridos si existen
                    val requiredHeaders = mutableMapOf<String, String>()
                    val headersJson = json.optJSONObject("required_headers")
                    if (headersJson != null) {
                        headersJson.keys().forEach { key ->
                            headersJson.getString(key)?.let { value ->
                                requiredHeaders[key] = value
                            }
                        }
                    }

                    if (directUrl.isNotBlank()) {
                        Log.d(TAG, "Extracted successfully: ${directUrl.take(80)}... (provider=$provider, type=$type, headers=${requiredHeaders.keys})")
                        Result.success(VideoExtractResult(directUrl, provider, type, requiredHeaders.toMap()))
                    } else {
                        Log.w(TAG, "Response successful but no URL: $body")
                        Result.failure(Exception("Respuesta sin URL"))
                    }
                } else {
                    val errorMsg = try {
                        JSONObject(body).optString("detail", "Error ${response.code}")
                    } catch (e: Exception) {
                        "Error ${response.code}: ${body.take(100)}"
                    }
                    Log.w(TAG, "Failed to extract: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Timeout extracting embed URL", e)
                Result.failure(Exception("Timeout al extraer el video (35s)"))
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting embed URL", e)
                Result.failure(e)
            }
        }
    }
}

data class VideoExtractResult(
    val url: String,
    val provider: String,
    val type: String,  // "mp4" o "hls"
    val headers: Map<String, String> = emptyMap(),  // Headers requeridos (Origin, Referer, etc.)
)
