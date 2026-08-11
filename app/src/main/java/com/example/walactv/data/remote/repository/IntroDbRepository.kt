package com.example.walactv.data.remote.repository

import android.util.Log
import android.util.LruCache
import com.google.gson.Gson
import com.example.walactv.data.model.SkipSegment
import com.example.walactv.data.model.SkipSegments
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

typealias IntroDbSegment = SkipSegment
typealias IntroDbSegments = SkipSegments

class IntroDbRepository @javax.inject.Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val cache = LruCache<String, IntroDbSegments>(50)

    suspend fun getSegments(imdbId: String, season: Int, episode: Int): IntroDbSegments? {
        val cacheKey = "$imdbId:$season:$episode"
        cache.get(cacheKey)?.let { return it }

        return try {
            val url = "https://api.introdb.app/segments?imdb_id=$imdbId&season=$season&episode=$episode"
            Log.d(TAG, "Fetching IntroDB segments: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "WalacTV/AndroidTV")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "IntroDB returned ${response.code} for $cacheKey")
                return null
            }

            val body = response.body?.string() ?: return null
            val segments = gson.fromJson(body, IntroDbSegments::class.java)
            cache.put(cacheKey, segments)
            Log.d(TAG, "IntroDB segments: intro=${segments.intro?.startMs}-${segments.intro?.endMs} recap=${segments.recap?.startMs}-${segments.recap?.endMs} outro=${segments.outro?.startMs}-${segments.outro?.endMs}")
            segments
        } catch (e: Exception) {
            Log.w(TAG, "IntroDB fetch failed", e)
            null
        }
    }

    fun getSegmentToSkip(
        segments: IntroDbSegments?,
        positionMs: Long,
    ): Long? {
        if (segments == null) return null
        val bufferMs = 2000L

        segments.intro?.let {
            val startMs = it.startMs ?: return@let
            val endMs = it.endMs ?: return@let
            if (positionMs in startMs..(endMs + bufferMs)) return endMs
        }
        segments.recap?.let {
            val startMs = it.startMs ?: return@let
            val endMs = it.endMs ?: return@let
            if (positionMs in startMs..(endMs + bufferMs)) return endMs
        }
        segments.outro?.let {
            val startMs = it.startMs ?: return@let
            if (positionMs >= startMs) return it.endMs ?: return@let
        }
        return null
    }

    companion object {
        private const val TAG = "IntroDbRepo"
    }
}
