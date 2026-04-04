package com.example.walactv.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.walactv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    }

    // Stats from server
    data class ContentStats(
        val total: Int,
        val generatedAt: String
    )

    // ── Stats from server ──────────────────────────────────────────────

    suspend fun getChannelsStats(token: String): ContentStats? = withContext(Dispatchers.IO) {
        try {
            val url = "${BuildConfig.IPTV_BASE_URL}/api/content/stats?content_type=channels"
            val json = getJsonFromUrl(url, token) ?: return@withContext null
            val channels = json.optJSONObject("channels") ?: return@withContext null
            ContentStats(
                total = channels.optInt("total", 0),
                generatedAt = channels.optString("generatedAt", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting channels stats", e)
            null
        }
    }

    suspend fun getMoviesStats(token: String): ContentStats? = withContext(Dispatchers.IO) {
        try {
            val url = "${BuildConfig.IPTV_BASE_URL}/api/content/stats?content_type=movies"
            val json = getJsonFromUrl(url, token) ?: return@withContext null
            val movies = json.optJSONObject("movies") ?: return@withContext null
            ContentStats(
                total = movies.optInt("total", 0),
                generatedAt = movies.optString("generatedAt", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting movies stats", e)
            null
        }
    }

    suspend fun getSeriesStats(token: String): ContentStats? = withContext(Dispatchers.IO) {
        try {
            val url = "${BuildConfig.IPTV_BASE_URL}/api/content/stats?content_type=series"
            val json = getJsonFromUrl(url, token) ?: return@withContext null
            val series = json.optJSONObject("series") ?: return@withContext null
            ContentStats(
                total = series.optInt("total", 0),
                generatedAt = series.optString("generatedAt", "")
            )
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

        if (localCount == 0) {
            Log.d(TAG, "needsSyncChannels: local DB empty (count=0), needs=true")
            return@withContext true
        }

        if (serverGenerated.isEmpty() && localGenerated.isEmpty()) {
            Log.d(TAG, "needsSyncChannels: both generatedAt empty, localCount=$localCount, needs=false")
            return@withContext false
        }

        val needsSync = localGenerated != serverGenerated
        Log.d(TAG, "needsSyncChannels: server=$serverGenerated, local=$localGenerated, localCount=$localCount, needs=$needsSync")
        needsSync
    }

    suspend fun needsSyncMovies(token: String): Boolean = withContext(Dispatchers.IO) {
        val localCount = database.movieDao().getCount()
        val stats = getMoviesStats(token)
        val localGenerated = getLocalGeneratedAt("movies")
        val serverGenerated = stats?.generatedAt ?: ""

        if (localCount == 0) {
            Log.d(TAG, "needsSyncMovies: local DB empty (count=0), needs=true")
            return@withContext true
        }

        if (serverGenerated.isEmpty() && localGenerated.isEmpty()) {
            Log.d(TAG, "needsSyncMovies: both generatedAt empty, localCount=$localCount, needs=false")
            return@withContext false
        }

        val needsSync = localGenerated != serverGenerated
        Log.d(TAG, "needsSyncMovies: server=$serverGenerated, local=$localGenerated, localCount=$localCount, needs=$needsSync")
        needsSync
    }

    suspend fun needsSyncSeries(token: String): Boolean = withContext(Dispatchers.IO) {
        val localCount = database.seriesDao().getCount()
        val stats = getSeriesStats(token)
        val localGenerated = getLocalGeneratedAt("series")
        val serverGenerated = stats?.generatedAt ?: ""

        if (localCount == 0) {
            Log.d(TAG, "needsSyncSeries: local DB empty (count=0), needs=true")
            return@withContext true
        }

        if (serverGenerated.isEmpty() && localGenerated.isEmpty()) {
            Log.d(TAG, "needsSyncSeries: both generatedAt empty, localCount=$localCount, needs=false")
            return@withContext false
        }

        val needsSync = localGenerated != serverGenerated
        Log.d(TAG, "needsSyncSeries: server=$serverGenerated, local=$localGenerated, localCount=$localCount, needs=$needsSync")
        needsSync
    }

    // ── Sync from API ──────────────────────────────────────────────────

    suspend fun syncChannels(token: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Syncing channels from API")
            val url = "${BuildConfig.IPTV_BASE_URL}/api/full/channels"
            val json = downloadGzipJson(url, token) ?: return@withContext Result.failure(Exception("Failed to download channels"))

            val channelsArray = json.optJSONArray("items") ?: JSONArray()
            Log.d(TAG, "syncChannels: found ${channelsArray.length()} items in response")
            val channels = mutableListOf<ChannelEntity>()

            for (i in 0 until channelsArray.length()) {
                val item = channelsArray.getJSONObject(i)
                channels.add(
                    ChannelEntity(
                        id = item.optString("id", ""),
                        numero = if (item.has("numero") && !item.isNull("numero")) item.optInt("numero") else null,
                        providerId = item.optString("provider_id", ""),
                        logo = item.optString("logo", ""),
                        country = item.optString("country", ""),
                        nombreNormalizado = item.optString("nombre_normalizado", ""),
                        grupoNormalizado = item.optString("grupo_normalizado", "")
                    )
                )
            }

            database.channelDao().deleteAll()
            database.channelDao().insertAll(channels)

            val generatedAt = json.optString("generated_at", "")
            prefs.edit().putString(KEY_CHANNELS_GENERATED_AT, generatedAt).apply()
            prefs.edit().putInt(KEY_CHANNELS_TOTAL, channels.size).apply()

            Log.d(TAG, "Synced ${channels.size} channels")
            Result.success(channels.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing channels", e)
            Result.failure(e)
        }
    }

    suspend fun syncMovies(token: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Syncing movies from API")
            val url = "${BuildConfig.IPTV_BASE_URL}/api/full/movies"
            val json = downloadGzipJson(url, token) ?: return@withContext Result.failure(Exception("Failed to download movies"))

            val moviesArray = json.optJSONArray("items") ?: JSONArray()
            Log.d(TAG, "syncMovies: found ${moviesArray.length()} items in response")
            val movies = mutableListOf<MovieEntity>()

            for (i in 0 until moviesArray.length()) {
                val item = moviesArray.getJSONObject(i)
                movies.add(
                    MovieEntity(
                        id = item.optString("id", ""),
                        providerId = item.optString("provider_id", ""),
                        logo = item.optString("logo", ""),
                        country = item.optString("country", ""),
                        nombreNormalizado = item.optString("nombre_normalizado", ""),
                        grupoNormalizado = item.optString("grupo_normalizado", "")
                    )
                )
            }

            database.movieDao().deleteAll()
            database.movieDao().insertAll(movies)

            val generatedAt = json.optString("generated_at", "")
            prefs.edit().putString(KEY_MOVIES_GENERATED_AT, generatedAt).apply()
            prefs.edit().putInt(KEY_MOVIES_TOTAL, movies.size).apply()

            Log.d(TAG, "Synced ${movies.size} movies")
            Result.success(movies.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing movies", e)
            Result.failure(e)
        }
    }

    suspend fun syncSeries(token: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Syncing series from API")
            val url = "${BuildConfig.IPTV_BASE_URL}/api/full/series"
            val json = downloadGzipJson(url, token) ?: return@withContext Result.failure(Exception("Failed to download series"))

            val seriesArray = json.optJSONArray("items") ?: JSONArray()
            Log.d(TAG, "syncSeries: found ${seriesArray.length()} items in response")
            val series = mutableListOf<SeriesEntity>()

            for (i in 0 until seriesArray.length()) {
                val item = seriesArray.getJSONObject(i)
                series.add(
                    SeriesEntity(
                        id = item.optString("id", ""),
                        providerId = item.optString("provider_id", ""),
                        logo = item.optString("logo", ""),
                        country = item.optString("country", ""),
                        temporada = item.optInt("temporada", 0),
                        episodio = item.optInt("episodio", 0),
                        serieName = item.optString("serie_name", ""),
                        nombreNormalizado = item.optString("nombre_normalizado", ""),
                        grupoNormalizado = item.optString("grupo_normalizado", "")
                    )
                )
            }

            database.seriesDao().deleteAll()
            database.seriesDao().insertAll(series)

            val generatedAt = json.optString("generated_at", "")
            prefs.edit().putString(KEY_SERIES_GENERATED_AT, generatedAt).apply()
            prefs.edit().putInt(KEY_SERIES_TOTAL, series.size).apply()

            Log.d(TAG, "Synced ${series.size} series")
            Result.success(series.size)
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
        val result = database.seriesDao().search(query)
        Log.d(TAG, "searchSeries($query): returning ${result.size} entities")
        result
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

    private fun downloadGzipJson(urlString: String, token: String): JSONObject? {
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
                return null
            }

            val inputStream = if (conn.contentEncoding == "gzip") {
                GZIPInputStream(conn.inputStream)
            } else {
                conn.inputStream
            }

            val body = inputStream.bufferedReader().use(BufferedReader::readText)
            JSONObject(body)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading gzip JSON", e)
            null
        } finally {
            conn.disconnect()
        }
    }
}
