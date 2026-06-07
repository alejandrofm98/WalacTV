package com.example.walactv.local

import android.content.Context
import android.content.SharedPreferences
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import androidx.core.content.edit
import com.example.walactv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentCacheManager @Inject constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val database = ContentDatabase.getDatabase(context)

    companion object {
        private const val TAG = "ContentCacheManager"
        private const val PREFS_NAME = "content_cache_prefs"

        private const val KEY_CHANNELS_GENERATED_AT = "channels_generated_at"
        private const val KEY_MOVIES_GENERATED_AT = "movies_generated_at"
        private const val KEY_SERIES_GENERATED_AT = "series_generated_at"

        private const val KEY_CHANNELS_TOTAL = "channels_total"
        private const val KEY_MOVIES_TOTAL = "movies_total"
        private const val KEY_SERIES_TOTAL = "series_total"

        private const val BATCH_SIZE = 500
    }

    data class ContentStats(
        val total: Int,
        val generatedAt: String
    )

    // ── Stats from server ──────────────────────────────────────────────

    suspend fun getChannelsStats(token: String): ContentStats? = withContext(Dispatchers.IO) {
        try {
            val url = "${BuildConfig.IPTV_BASE_URL}/api/content/stats?content_type=channels"
            Log.d(TAG, "getChannelsStats: requesting $url")
            val json = getJsonFromUrl(url, token)
            if (json == null) {
                Log.w(TAG, "getChannelsStats: failed to fetch JSON from server (null response)")
                return@withContext null
            }
            val channels = json.optJSONObject("channels")
            if (channels == null) {
                Log.w(TAG, "getChannelsStats: no 'channels' object in response, raw: ${json.toString(200)}")
                return@withContext null
            }
            val result = ContentStats(
                total = channels.optInt("total", 0),
                generatedAt = channels.optString("generatedAt", "")
            )
            Log.d(TAG, "getChannelsStats: total=${result.total}, generatedAt='${result.generatedAt}'")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error getting channels stats", e)
            null
        }
    }

    suspend fun getMoviesStats(token: String): ContentStats? = withContext(Dispatchers.IO) {
        try {
            val url = "${BuildConfig.IPTV_BASE_URL}/api/content/stats?content_type=movies"
            Log.d(TAG, "getMoviesStats: requesting $url")
            val json = getJsonFromUrl(url, token)
            if (json == null) {
                Log.w(TAG, "getMoviesStats: failed to fetch JSON from server (null response)")
                return@withContext null
            }
            val movies = json.optJSONObject("movies")
            if (movies == null) {
                Log.w(TAG, "getMoviesStats: no 'movies' object in response, raw: ${json.toString(200)}")
                return@withContext null
            }
            val result = ContentStats(
                total = movies.optInt("total", 0),
                generatedAt = movies.optString("generatedAt", "")
            )
            Log.d(TAG, "getMoviesStats: total=${result.total}, generatedAt='${result.generatedAt}'")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error getting movies stats", e)
            null
        }
    }

    suspend fun getSeriesStats(token: String): ContentStats? = withContext(Dispatchers.IO) {
        try {
            val url = "${BuildConfig.IPTV_BASE_URL}/api/content/stats?content_type=series"
            Log.d(TAG, "getSeriesStats: requesting $url")
            val json = getJsonFromUrl(url, token)
            if (json == null) {
                Log.w(TAG, "getSeriesStats: failed to fetch JSON from server (null response)")
                return@withContext null
            }
            val series = json.optJSONObject("series")
            if (series == null) {
                Log.w(TAG, "getSeriesStats: no 'series' object in response, raw: ${json.toString(200)}")
                return@withContext null
            }
            val result = ContentStats(
                total = series.optInt("total", 0),
                generatedAt = series.optString("generatedAt", "")
            )
            Log.d(TAG, "getSeriesStats: total=${result.total}, generatedAt='${result.generatedAt}'")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error getting series stats", e)
            null
        }
    }

    // ── Sync check ─────────────────────────────────────────────────────

    private fun shouldSync(localGenerated: String, serverGenerated: String): Boolean {
        if (serverGenerated.isEmpty()) return false
        if (localGenerated.isEmpty()) return true
        val local = parseTimestamp(localGenerated) ?: return localGenerated != serverGenerated
        val server = parseTimestamp(serverGenerated) ?: return localGenerated != serverGenerated
        return server.isAfter(local)
    }

    private fun parseTimestamp(ts: String): Instant? {
        return try {
            OffsetDateTime.parse(ts).toInstant()
        } catch (e: Exception) {
            try {
                LocalDateTime.parse(ts).toInstant(ZoneOffset.UTC)
            } catch (e2: Exception) {
                Log.w(TAG, "parseTimestamp: failed to parse '$ts'", e2)
                null
            }
        }
    }

    fun getLocalGeneratedAt(type: String): String {
        return when (type) {
            "channels" -> prefs.getString(KEY_CHANNELS_GENERATED_AT, "") ?: ""
            "movies" -> prefs.getString(KEY_MOVIES_GENERATED_AT, "") ?: ""
            "series" -> prefs.getString(KEY_SERIES_GENERATED_AT, "") ?: ""
            else -> ""
        }
    }

    suspend fun needsSyncChannels(token: String): Boolean = withContext(Dispatchers.IO) {
        val localCount = database.channelDao().getCount()
        val stats = getChannelsStats(token)
        val localGenerated = getLocalGeneratedAt("channels")
        val serverGenerated = stats?.generatedAt ?: ""

        Log.d(TAG, "needsSyncChannels: localCount=$localCount, statsReceived=${stats != null}")
        Log.d(TAG, "needsSyncChannels: localGenerated='$localGenerated'")
        Log.d(TAG, "needsSyncChannels: serverGenerated='$serverGenerated' (stats was null: ${stats == null})")

        if (localCount == 0) {
            Log.d(TAG, "needsSyncChannels: DECISION -> needsSync=true (local DB empty)")
            return@withContext true
        }

        if (serverGenerated.isEmpty() && localGenerated.isEmpty()) {
            Log.d(TAG, "needsSyncChannels: DECISION -> needsSync=false (both generatedAt empty)")
            return@withContext false
        }

        val needsSync = shouldSync(localGenerated, serverGenerated)
        Log.d(TAG, "needsSyncChannels: comparison: '$localGenerated' vs '$serverGenerated' = $needsSync")
        Log.d(TAG, "needsSyncChannels: DECISION -> needsSync=$needsSync")
        needsSync
    }

    // ── Sync from API (streaming + batch inserts) ──────────────────────

    private fun openJsonStream(urlString: String, token: String): Pair<HttpURLConnection, JsonReader>? {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Encoding", "gzip")
            conn.connectTimeout = 60_000
            conn.readTimeout = 60_000

            val status = conn.responseCode
            if (status !in 200..299) {
                Log.e(TAG, "HTTP $status")
                conn.disconnect()
                return null
            }

            val inputStream = if (conn.contentEncoding == "gzip") {
                GZIPInputStream(conn.inputStream)
            } else {
                conn.inputStream
            }

            val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
            Pair(conn, reader)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening json stream", e)
            conn.disconnect()
            null
        }
    }

    suspend fun syncChannels(token: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Syncing channels from API")
            val url = "${BuildConfig.IPTV_BASE_URL}/api/full/channels"
            var generatedAt: String? = null
            val batch = mutableListOf<ChannelEntity>()
            var totalCount = 0

            val connection = openJsonStream(url, token)
                ?: return@withContext Result.failure(Exception("Failed to download channels"))

            val (conn, reader) = connection
            try {
                reader.beginObject()
                var fieldCount = 0
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    fieldCount++
                    Log.d(TAG, "syncChannels: read field #$fieldCount name='$name'")
                    when (name) {
                        "generated_at" -> {
                            generatedAt = reader.nextString()
                            Log.d(TAG, "syncChannels: read generatedAt='$generatedAt'")
                        }
                        "items" -> {
                            reader.beginArray()
                            var itemCount = 0
                            while (reader.hasNext()) {
                                batch.add(parseChannelObject(reader))
                                totalCount++
                                itemCount++
                                if (itemCount % 10000 == 0) {
                                    Log.d(TAG, "syncChannels: read $itemCount items so far")
                                }
                            }
                            reader.endArray()
                            Log.d(TAG, "syncChannels: finished items array, total=$totalCount")
                        }
                        else -> {
                            reader.skipValue()
                            Log.d(TAG, "syncChannels: skipped unknown field '$name'")
                        }
                    }
                }
                reader.endObject()
                Log.d(TAG, "syncChannels: finished parsing root object, fieldCount=$fieldCount, totalCount=$totalCount, generatedAt='$generatedAt'")

                database.channelDao().replaceAll(batch)

                Log.d(TAG, "syncChannels: generatedAt from JSON='${generatedAt}', totalCount=$totalCount")
                prefs.edit {
                    putString(KEY_CHANNELS_GENERATED_AT, generatedAt ?: "")
                    putInt(KEY_CHANNELS_TOTAL, totalCount)
                }
                Log.d(TAG, "syncChannels: saved generatedAt to prefs, verifying: '${prefs.getString(KEY_CHANNELS_GENERATED_AT, "MISSING")}'")
            } catch (e: Exception) {
                Log.e(TAG, "syncChannels: exception during JSON parsing", e)
                throw e
            } finally {
                try { reader.close() } catch (_: Exception) {}
                conn.disconnect()
            }

            Log.d(TAG, "Synced $totalCount channels")
            Result.success(totalCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing channels", e)
            Result.failure(e)
        }
    }

    // ── Local queries ──────────────────────────────────────────────────

    suspend fun getChannelsByCountry(country: String): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val result = database.channelDao().getByCountryPaged(country, Int.MAX_VALUE, 0)
        Log.d(TAG, "getChannelsByCountry($country): returning ${result.size} entities")
        result
    }

    suspend fun searchChannels(query: String, country: String?, group: String?): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val result = when {
            country != null && group != null -> database.channelDao().searchByCountryAndGroup(query, country, group)
            country != null -> database.channelDao().searchByCountry(query, country)
            group != null -> database.channelDao().searchByGroup(query, group)
            else -> database.channelDao().search(query)
        }
        Log.d(TAG, "searchChannels($query, country=$country, group=$group): returning ${result.size} entities")
        result
    }

    // ── Paged queries (sliding window, max 100 items in memory) ──────────

    suspend fun getChannelsPaged(country: String?, group: String?, page: Int, pageSize: Int = 50): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val offset = page * pageSize
        val result = when {
            country != null && group != null -> database.channelDao().getByCountryAndGroupPaged(country, group, pageSize, offset)
            country != null -> database.channelDao().getByCountryPaged(country, pageSize, offset)
            group != null -> database.channelDao().getByGroupPaged(group, pageSize, offset)
            else -> database.channelDao().getAllPaged(pageSize, offset)
        }
        Log.d(TAG, "getChannelsPaged(page=$page, pageSize=$pageSize, country=$country, group=$group): returning ${result.size} entities")
        result
    }

    suspend fun getChannelsTotalCount(country: String?, group: String?): Int = withContext(Dispatchers.IO) {
        val count = when {
            country != null && group != null -> database.channelDao().getCountByCountryAndGroup(country, group)
            country != null -> database.channelDao().getCountByCountry(country)
            group != null -> database.channelDao().getCountByGroup(group)
            else -> database.channelDao().getCount()
        }
        Log.d(TAG, "getChannelsTotalCount(country=$country, group=$group): $count")
        count
    }

    // ── Local filters (derived from cached data) ────────────────────────

    suspend fun getLocalChannelFilters(country: String? = null): com.example.walactv.CatalogFilters = withContext(Dispatchers.IO) {
        val countries = database.channelDao().getDistinctCountries()
        val groups = if (country != null) {
            database.channelDao().getDistinctGroupsByCountry(country)
        } else {
            database.channelDao().getDistinctGroups()
        }
        com.example.walactv.CatalogFilters(
            countries = countries.map { com.example.walactv.CatalogFilterOption(it, it) },
            groups = groups.map { com.example.walactv.CatalogFilterOption(it, it) }
        )
    }

    // ── Helper methods ─────────────────────────────────────────────────

    private fun getJsonFromUrl(urlString: String, token: String): JSONObject? {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000

            val status = conn.responseCode
            if (status !in 200..299) {
                Log.e(TAG, "HTTP $status")
                return null
            }

            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            JSONObject(body)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching JSON", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun parseChannelObject(reader: JsonReader): ChannelEntity {
        var id = ""
        var numero: Int? = null
        var providerId = ""
        var logo = ""
        var country = ""
        var nombreNormalizado = ""
        var grupoNormalizado = ""

        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (name) {
                "id" -> id = reader.nextString()
                "numero" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        numero = reader.nextInt()
                    }
                }
                "provider_id" -> providerId = reader.nextString()
                "logo" -> logo = reader.nextString()
                "country" -> country = reader.nextString()
                "nombre_normalizado" -> nombreNormalizado = reader.nextString()
                "grupo_normalizado" -> grupoNormalizado = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return ChannelEntity(
            id = id,
            numero = numero,
            providerId = providerId,
            logo = logo,
            country = country,
            nombreNormalizado = nombreNormalizado,
            grupoNormalizado = grupoNormalizado
        )
    }

    private fun parseMovieObject(reader: JsonReader): MovieEntity {
        var id = ""
        var providerId = ""
        var nombre = ""
        var logo = ""
        var country = ""
        var nombreNormalizado = ""
        var grupoNormalizado = ""

        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (name) {
                "id" -> id = reader.nextString()
                "provider_id" -> providerId = reader.nextString()
                "nombre" -> nombre = reader.nextString()
                "logo" -> logo = reader.nextString()
                "country" -> country = reader.nextString()
                "nombre_normalizado" -> nombreNormalizado = reader.nextString()
                "grupo_normalizado" -> grupoNormalizado = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return MovieEntity(
            id = id,
            providerId = providerId,
            nombre = nombre,
            logo = logo,
            country = country,
            nombreNormalizado = nombreNormalizado,
            grupoNormalizado = grupoNormalizado
        )
    }

    private fun parseSeriesObject(reader: JsonReader): SeriesEntity {
        var id = ""
        var providerId = ""
        var logo = ""
        var country = ""
        var temporada = 0
        var episodio = 0
        var serieName = ""
        var nombreNormalizado = ""
        var grupoNormalizado = ""

        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (name) {
                "id" -> id = reader.nextString()
                "provider_id" -> providerId = reader.nextString()
                "logo" -> logo = reader.nextString()
                "country" -> country = reader.nextString()
                "temporada" -> temporada = reader.nextInt()
                "episodio" -> episodio = reader.nextInt()
                "serie_name" -> serieName = reader.nextString()
                "nombre_normalizado" -> nombreNormalizado = reader.nextString()
                "grupo_normalizado" -> grupoNormalizado = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return SeriesEntity(
            id = id,
            providerId = providerId,
            logo = logo,
            country = country,
            temporada = temporada,
            episodio = episodio,
            serieName = serieName,
            nombreNormalizado = nombreNormalizado,
            grupoNormalizado = grupoNormalizado
        )
    }
}
