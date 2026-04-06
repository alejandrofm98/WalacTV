package com.example.walactv.local

import android.content.Context
import android.content.SharedPreferences
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import com.example.walactv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class ContentCacheManager(private val context: Context) {

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

        val needsSync = localGenerated != serverGenerated
        Log.d(TAG, "needsSyncChannels: comparison: '$localGenerated' != '$serverGenerated' = $needsSync")
        Log.d(TAG, "needsSyncChannels: DECISION -> needsSync=$needsSync")
        needsSync
    }

    suspend fun needsSyncMovies(token: String): Boolean = withContext(Dispatchers.IO) {
        val localCount = database.movieDao().getCount()
        val stats = getMoviesStats(token)
        val localGenerated = getLocalGeneratedAt("movies")
        val serverGenerated = stats?.generatedAt ?: ""

        Log.d(TAG, "needsSyncMovies: localCount=$localCount, statsReceived=${stats != null}")
        Log.d(TAG, "needsSyncMovies: localGenerated='$localGenerated'")
        Log.d(TAG, "needsSyncMovies: serverGenerated='$serverGenerated' (stats was null: ${stats == null})")

        if (localCount == 0) {
            Log.d(TAG, "needsSyncMovies: DECISION -> needsSync=true (local DB empty)")
            return@withContext true
        }

        if (serverGenerated.isEmpty() && localGenerated.isEmpty()) {
            Log.d(TAG, "needsSyncMovies: DECISION -> needsSync=false (both generatedAt empty)")
            return@withContext false
        }

        val needsSync = localGenerated != serverGenerated
        Log.d(TAG, "needsSyncMovies: comparison: '$localGenerated' != '$serverGenerated' = $needsSync")
        Log.d(TAG, "needsSyncMovies: DECISION -> needsSync=$needsSync")
        needsSync
    }

    suspend fun needsSyncSeries(token: String): Boolean = withContext(Dispatchers.IO) {
        val localCount = database.seriesDao().getCount()
        val stats = getSeriesStats(token)
        val localGenerated = getLocalGeneratedAt("series")
        val serverGenerated = stats?.generatedAt ?: ""

        Log.d(TAG, "needsSyncSeries: localCount=$localCount, statsReceived=${stats != null}")
        Log.d(TAG, "needsSyncSeries: localGenerated='$localGenerated'")
        Log.d(TAG, "needsSyncSeries: serverGenerated='$serverGenerated' (stats was null: ${stats == null})")

        if (localCount == 0) {
            Log.d(TAG, "needsSyncSeries: DECISION -> needsSync=true (local DB empty)")
            return@withContext true
        }

        if (serverGenerated.isEmpty() && localGenerated.isEmpty()) {
            Log.d(TAG, "needsSyncSeries: DECISION -> needsSync=false (both generatedAt empty)")
            return@withContext false
        }

        val needsSync = localGenerated != serverGenerated
        Log.d(TAG, "needsSyncSeries: comparison: '$localGenerated' != '$serverGenerated' = $needsSync")
        Log.d(TAG, "needsSyncSeries: DECISION -> needsSync=$needsSync")
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
                database.channelDao().deleteAll()

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
                                if (batch.size >= BATCH_SIZE) {
                                    database.channelDao().insertAll(batch)
                                    batch.clear()
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

                if (batch.isNotEmpty()) {
                    database.channelDao().insertAll(batch)
                }

                Log.d(TAG, "syncChannels: generatedAt from JSON='${generatedAt}', totalCount=$totalCount")
                prefs.edit()
                    .putString(KEY_CHANNELS_GENERATED_AT, generatedAt ?: "")
                    .putInt(KEY_CHANNELS_TOTAL, totalCount)
                    .apply()
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

    suspend fun syncMovies(token: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Syncing movies from API")
            val url = "${BuildConfig.IPTV_BASE_URL}/api/full/movies"
            var generatedAt: String? = null
            val batch = mutableListOf<MovieEntity>()
            var totalCount = 0

            val connection = openJsonStream(url, token)
                ?: return@withContext Result.failure(Exception("Failed to download movies"))

            val (conn, reader) = connection
            try {
                database.movieDao().deleteAll()

                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    when (name) {
                        "generated_at" -> generatedAt = reader.nextString()
                        "items" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                batch.add(parseMovieObject(reader))
                                totalCount++
                                if (batch.size >= BATCH_SIZE) {
                                    database.movieDao().insertAll(batch)
                                    batch.clear()
                                }
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()

                if (batch.isNotEmpty()) {
                    database.movieDao().insertAll(batch)
                }

                Log.d(TAG, "syncMovies: generatedAt from JSON='${generatedAt}', totalCount=$totalCount")
                prefs.edit()
                    .putString(KEY_MOVIES_GENERATED_AT, generatedAt ?: "")
                    .putInt(KEY_MOVIES_TOTAL, totalCount)
                    .apply()
                Log.d(TAG, "syncMovies: saved generatedAt to prefs, verifying: '${prefs.getString(KEY_MOVIES_GENERATED_AT, "MISSING")}'")
            } finally {
                try { reader.close() } catch (_: Exception) {}
                conn.disconnect()
            }

            Log.d(TAG, "Synced $totalCount movies")
            Result.success(totalCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing movies", e)
            Result.failure(e)
        }
    }

    suspend fun syncSeries(token: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Syncing series from API")
            val url = "${BuildConfig.IPTV_BASE_URL}/api/full/series"
            var generatedAt: String? = null
            val batch = mutableListOf<SeriesEntity>()
            var totalCount = 0

            val connection = openJsonStream(url, token)
                ?: return@withContext Result.failure(Exception("Failed to download series"))

            val (conn, reader) = connection
            try {
                database.seriesDao().deleteAll()

                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    when (name) {
                        "generated_at" -> generatedAt = reader.nextString()
                        "items" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                batch.add(parseSeriesObject(reader))
                                totalCount++
                                if (batch.size >= BATCH_SIZE) {
                                    database.seriesDao().insertAll(batch)
                                    batch.clear()
                                    Log.d(TAG, "syncSeries: inserted batch, total so far: $totalCount")
                                }
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()

                if (batch.isNotEmpty()) {
                    database.seriesDao().insertAll(batch)
                }

                Log.d(TAG, "syncSeries: generatedAt from JSON='${generatedAt}', totalCount=$totalCount")
                prefs.edit()
                    .putString(KEY_SERIES_GENERATED_AT, generatedAt ?: "")
                    .putInt(KEY_SERIES_TOTAL, totalCount)
                    .apply()
                Log.d(TAG, "syncSeries: saved generatedAt to prefs, verifying: '${prefs.getString(KEY_SERIES_GENERATED_AT, "MISSING")}'")
            } finally {
                try { reader.close() } catch (_: Exception) {}
                conn.disconnect()
            }

            Log.d(TAG, "Synced $totalCount series")
            Result.success(totalCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing series", e)
            Result.failure(e)
        }
    }

    // ── Local queries ──────────────────────────────────────────────────

    suspend fun getAllChannels(): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val result = database.channelDao().getAllPaged(Int.MAX_VALUE, 0)
        Log.d(TAG, "getAllChannels: returning ${result.size} entities")
        result
    }

    suspend fun getChannelsByCountry(country: String): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val result = database.channelDao().getByCountryPaged(country, Int.MAX_VALUE, 0)
        Log.d(TAG, "getChannelsByCountry($country): returning ${result.size} entities")
        result
    }

    suspend fun getChannelsByGroup(group: String): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val result = database.channelDao().getByGroupPaged(group, Int.MAX_VALUE, 0)
        Log.d(TAG, "getChannelsByGroup($group): returning ${result.size} entities")
        result
    }

    suspend fun getChannelsByCountryAndGroup(country: String, group: String): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val result = database.channelDao().getByCountryAndGroupPaged(country, group, Int.MAX_VALUE, 0)
        Log.d(TAG, "getChannelsByCountryAndGroup($country, $group): returning ${result.size} entities")
        result
    }

    suspend fun searchChannels(query: String): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val result = database.channelDao().search(query)
        Log.d(TAG, "searchChannels($query): returning ${result.size} entities")
        result
    }

    suspend fun getChannelsByIds(ids: List<String>): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val result = if (ids.isEmpty()) emptyList<ChannelEntity>()
        else database.channelDao().getByIds(ids)
        Log.d(TAG, "getChannelsByIds: returning ${result.size} entities")
        result
    }

    suspend fun getChannelsCount(): Int = withContext(Dispatchers.IO) {
        val count = database.channelDao().getCount()
        Log.d(TAG, "getChannelsCount: $count")
        count
    }

    suspend fun getAllMovies(): List<MovieEntity> = withContext(Dispatchers.IO) {
        val result = database.movieDao().getAllPaged(Int.MAX_VALUE, 0)
        Log.d(TAG, "getAllMovies: returning ${result.size} entities")
        result
    }

    suspend fun getMoviesByGroup(group: String): List<MovieEntity> = withContext(Dispatchers.IO) {
        val result = database.movieDao().getByGroupPaged(group, Int.MAX_VALUE, 0)
        Log.d(TAG, "getMoviesByGroup($group): returning ${result.size} entities")
        result
    }

    suspend fun getMoviesByCountry(country: String): List<MovieEntity> = withContext(Dispatchers.IO) {
        val result = database.movieDao().getByCountryPaged(country, Int.MAX_VALUE, 0)
        Log.d(TAG, "getMoviesByCountry($country): returning ${result.size} entities")
        result
    }

    suspend fun getMoviesByCountryAndGroup(country: String, group: String): List<MovieEntity> = withContext(Dispatchers.IO) {
        val result = database.movieDao().getByCountryAndGroupPaged(country, group, Int.MAX_VALUE, 0)
        Log.d(TAG, "getMoviesByCountryAndGroup($country, $group): returning ${result.size} entities")
        result
    }

    suspend fun searchMovies(query: String): List<MovieEntity> = withContext(Dispatchers.IO) {
        val result = database.movieDao().search(query)
        Log.d(TAG, "searchMovies($query): returning ${result.size} entities")
        result
    }

    suspend fun getMoviesCount(): Int = withContext(Dispatchers.IO) {
        val count = database.movieDao().getCount()
        Log.d(TAG, "getMoviesCount: $count")
        count
    }

    suspend fun getAllSeries(): List<SeriesEntity> = withContext(Dispatchers.IO) {
        val result = database.seriesDao().getAllPaged(Int.MAX_VALUE, 0)
        Log.d(TAG, "getAllSeries: returning ${result.size} entities")
        result
    }

    suspend fun getSeriesByGroup(group: String): List<SeriesEntity> = withContext(Dispatchers.IO) {
        val result = database.seriesDao().getByGroupPaged(group, Int.MAX_VALUE, 0)
        Log.d(TAG, "getSeriesByGroup($group): returning ${result.size} entities")
        result
    }

    suspend fun getSeriesByCountry(country: String): List<SeriesEntity> = withContext(Dispatchers.IO) {
        val result = database.seriesDao().getByCountryPaged(country, Int.MAX_VALUE, 0)
        Log.d(TAG, "getSeriesByCountry($country): returning ${result.size} entities")
        result
    }

    suspend fun getSeriesByCountryAndGroup(country: String, group: String): List<SeriesEntity> = withContext(Dispatchers.IO) {
        val result = database.seriesDao().getByCountryAndGroupPaged(country, group, Int.MAX_VALUE, 0)
        Log.d(TAG, "getSeriesByCountryAndGroup($country, $group): returning ${result.size} entities")
        result
    }

    suspend fun searchSeries(query: String): List<SeriesEntity> = withContext(Dispatchers.IO) {
        val total = database.seriesDao().getCount()
        Log.d(TAG, "searchSeries: DB has $total total episodes, searching for '$query'")
        val allSeries = database.seriesDao().getAllPaged(50000, 0)
        val uniqueSeries = allSeries.distinctBy { it.serieName }
        val filtered = uniqueSeries.filter { it.serieName.contains(query, ignoreCase = true) }
            .sortedBy { it.serieName }
            .take(100)
        Log.d(TAG, "searchSeries('$query'): $total episodes -> ${uniqueSeries.size} unique -> ${filtered.size} matches")
        if (filtered.isNotEmpty()) {
            Log.d(TAG, "searchSeries: first result serieName='${filtered.first().serieName}'")
        }
        filtered
    }

    suspend fun getSeriesCount(): Int = withContext(Dispatchers.IO) {
        val count = database.seriesDao().getCount()
        Log.d(TAG, "getSeriesCount: $count")
        count
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

    suspend fun getMoviesPaged(country: String?, group: String?, page: Int, pageSize: Int = 50): List<MovieEntity> = withContext(Dispatchers.IO) {
        val offset = page * pageSize
        val result = when {
            country != null && group != null -> database.movieDao().getByCountryAndGroupPaged(country, group, pageSize, offset)
            country != null -> database.movieDao().getByCountryPaged(country, pageSize, offset)
            group != null -> database.movieDao().getByGroupPaged(group, pageSize, offset)
            else -> database.movieDao().getAllPaged(pageSize, offset)
        }
        Log.d(TAG, "getMoviesPaged(page=$page, pageSize=$pageSize, country=$country, group=$group): returning ${result.size} entities")
        result
    }

    suspend fun getMoviesTotalCount(country: String?, group: String?): Int = withContext(Dispatchers.IO) {
        val count = when {
            country != null && group != null -> database.movieDao().getCountByCountryAndGroup(country, group)
            country != null -> database.movieDao().getCountByCountry(country)
            group != null -> database.movieDao().getCountByGroup(group)
            else -> database.movieDao().getCount()
        }
        Log.d(TAG, "getMoviesTotalCount(country=$country, group=$group): $count")
        count
    }

    suspend fun getSeriesPaged(country: String?, group: String?, page: Int, pageSize: Int = 50): List<SeriesEntity> = withContext(Dispatchers.IO) {
        val offset = page * pageSize
        val result = when {
            country != null && group != null -> database.seriesDao().getByCountryAndGroupPaged(country, group, pageSize, offset)
            country != null -> database.seriesDao().getByCountryPaged(country, pageSize, offset)
            group != null -> database.seriesDao().getByGroupPaged(group, pageSize, offset)
            else -> database.seriesDao().getAllPaged(pageSize, offset)
        }
        Log.d(TAG, "getSeriesPaged(page=$page, pageSize=$pageSize, country=$country, group=$group): returning ${result.size} entities")
        result
    }

    suspend fun getSeriesTotalCount(country: String?, group: String?): Int = withContext(Dispatchers.IO) {
        val count = when {
            country != null && group != null -> database.seriesDao().getCountByCountryAndGroup(country, group)
            country != null -> database.seriesDao().getCountByCountry(country)
            group != null -> database.seriesDao().getCountByGroup(group)
            else -> database.seriesDao().getCount()
        }
        Log.d(TAG, "getSeriesTotalCount(country=$country, group=$group): $count")
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

    suspend fun getLocalMovieFilters(country: String? = null): com.example.walactv.CatalogFilters = withContext(Dispatchers.IO) {
        val countries = database.movieDao().getDistinctCountries()
        val groups = if (country != null) {
            database.movieDao().getDistinctGroupsByCountry(country)
        } else {
            database.movieDao().getDistinctGroups()
        }
        com.example.walactv.CatalogFilters(
            countries = countries.map { com.example.walactv.CatalogFilterOption(it, it) },
            groups = groups.map { com.example.walactv.CatalogFilterOption(it, it) }
        )
    }

    suspend fun getLocalSeriesFilters(country: String? = null): com.example.walactv.CatalogFilters = withContext(Dispatchers.IO) {
        val countries = database.seriesDao().getDistinctCountries()
        val groups = if (country != null) {
            database.seriesDao().getDistinctGroupsByCountry(country)
        } else {
            database.seriesDao().getDistinctGroups()
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
