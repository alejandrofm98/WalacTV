package com.example.walactv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class M3uCatalogStore(private val context: Context) {

    private val credentialStore = CredentialStore(context)
    var progressListener: ((PlaylistLoadProgress) -> Unit)? = null

    suspend fun getCatalog(forceRefresh: Boolean = false): M3uCatalogSnapshot = withContext(Dispatchers.IO) {
        reportProgress(PlaylistLoadStage.CHECKING_CACHE, "Comprobando cache de la lista", 0)
        if (!forceRefresh) {
            fullMemorySnapshot?.let {
                Log.d(TAG, "Usando snapshot M3U en memoria")
                reportProgress(PlaylistLoadStage.READY, "Lista cargada desde memoria", 100)
                return@withContext it
            }

            loadPersistedSnapshot(fullSnapshotFile)?.let {
                Log.d(TAG, "Usando snapshot M3U persistido")
                fullMemorySnapshot = it
                reportProgress(PlaylistLoadStage.READY, "Lista cargada desde cache guardada", 100)
                return@withContext it
            }
        }

        ensureCacheReady(forceRefresh)
        Log.d(TAG, "Usando playlist M3U desde cache local: ${cacheFile.absolutePath}")
        loadFromCache().also {
            fullMemorySnapshot = it
            persistSnapshot(fullSnapshotFile, it)
            reportProgress(
                PlaylistLoadStage.READY,
                "Lista completa lista",
                100,
                detail = buildSnapshotDetail(it),
            )
        }
    }

    suspend fun getHomeSnapshot(forceRefresh: Boolean = false): M3uCatalogSnapshot = withContext(Dispatchers.IO) {
        reportProgress(PlaylistLoadStage.CHECKING_CACHE, "Comprobando cache rapida", 0)
        if (!forceRefresh) {
            summaryMemorySnapshot?.let {
                Log.d(TAG, "Usando resumen M3U en memoria")
                reportProgress(PlaylistLoadStage.READY, "Resumen cargado desde memoria", 100)
                return@withContext it
            }

            loadPersistedSnapshot(summarySnapshotFile)?.let {
                Log.d(TAG, "Usando resumen M3U persistido")
                summaryMemorySnapshot = it
                reportProgress(PlaylistLoadStage.READY, "Resumen cargado desde cache guardada", 100)
                return@withContext it
            }
        }

        ensureCacheReady(forceRefresh)
        Log.d(TAG, "Usando resumen M3U desde cache local: ${cacheFile.absolutePath}")
        loadSummaryFromCache().also {
            summaryMemorySnapshot = it
            persistSnapshot(summarySnapshotFile, it)
            reportProgress(
                PlaylistLoadStage.READY,
                "Resumen de lista listo",
                100,
                detail = buildSnapshotDetail(it),
            )
        }
    }

    suspend fun refreshNow(): M3uCatalogSnapshot = withContext(Dispatchers.IO) {
        Log.d(TAG, "Refrescando playlist M3U ahora")
        reportProgress(PlaylistLoadStage.DOWNLOADING, "Descargando lista IPTV", 0)
        downloadPlaylistToCache()
        saveLastUpdated(System.currentTimeMillis())
        fullMemorySnapshot = null
        summaryMemorySnapshot = null
        deletePersistedSnapshots()

        val fullSnapshot = loadFromCache().also {
            fullMemorySnapshot = it
            persistSnapshot(fullSnapshotFile, it)
        }
        loadSummaryFromCache().also {
            summaryMemorySnapshot = it
            persistSnapshot(summarySnapshotFile, it)
        }

        fullSnapshot
    }

    fun clearAllCache() {
        fullMemorySnapshot = null
        summaryMemorySnapshot = null
        cacheFile.delete()
        deletePersistedSnapshots()
        preferences.edit().remove(KEY_LAST_UPDATED).apply()
    }

    fun getLastUpdatedMillis(): Long = preferences.getLong(KEY_LAST_UPDATED, 0L)

    fun hasCache(): Boolean = cacheFile.exists()

    fun needsBackgroundRefresh(): Boolean = cacheFile.exists() && isCacheExpired()

    fun getCacheSizeBytes(): Long = if (cacheFile.exists()) cacheFile.length() else 0L

    private fun ensureCacheReady(forceRefresh: Boolean) {
        val shouldRefresh = forceRefresh || !cacheFile.exists() || isCacheExpired()
        if (shouldRefresh) {
            Log.d(TAG, "Descargando playlist M3U. forceRefresh=$forceRefresh cacheExists=${cacheFile.exists()}")
            reportProgress(PlaylistLoadStage.DOWNLOADING, "Descargando lista IPTV", 0)
            downloadPlaylistToCache()
            saveLastUpdated(System.currentTimeMillis())
            fullMemorySnapshot = null
            summaryMemorySnapshot = null
            deletePersistedSnapshots()
        }
    }

    private fun isCacheExpired(): Boolean {
        val lastUpdated = getLastUpdatedMillis()
        if (lastUpdated == 0L) return true
        return System.currentTimeMillis() - lastUpdated >= CACHE_TTL_MS
    }

    private fun loadFromCache(): M3uCatalogSnapshot {
        if (!cacheFile.exists()) return M3uCatalogSnapshot(emptyList(), emptyList(), emptyList())
        return cacheFile.bufferedReader().use {
            parsePlaylist(
                reader = it,
                limits = null,
                stage = PlaylistLoadStage.PARSING_FULL,
                progressLabel = "Parseando lista completa",
                totalBytes = cacheFile.length(),
            )
        }
    }

    private fun loadSummaryFromCache(): M3uCatalogSnapshot {
        if (!cacheFile.exists()) return M3uCatalogSnapshot(emptyList(), emptyList(), emptyList())
        return cacheFile.bufferedReader().use {
            parsePlaylist(
                reader = it,
                limits = HOME_LIMITS,
                stage = PlaylistLoadStage.PARSING_SUMMARY,
                progressLabel = "Parseando vista rapida",
                totalBytes = cacheFile.length(),
            )
        }
    }

    private fun loadPersistedSnapshot(file: File): M3uCatalogSnapshot? {
        if (!file.exists() || isCacheExpired()) return null

        return runCatching {
            val payload = JSONObject(file.readText())
            snapshotFromJson(payload)
        }.onFailure {
            Log.w(TAG, "No se pudo leer snapshot persistido ${file.name}", it)
            file.delete()
        }.getOrNull()
    }

    private fun persistSnapshot(file: File, snapshot: M3uCatalogSnapshot) {
        runCatching {
            file.writeText(snapshotToJson(snapshot).toString())
        }.onFailure {
            Log.w(TAG, "No se pudo guardar snapshot persistido ${file.name}", it)
        }
    }

    private fun deletePersistedSnapshots() {
        fullSnapshotFile.delete()
        summarySnapshotFile.delete()
    }

    private fun snapshotToJson(snapshot: M3uCatalogSnapshot): JSONObject {
        return JSONObject()
            .put("channels", itemsToJson(snapshot.channels))
            .put("movies", itemsToJson(snapshot.movies))
            .put("series", itemsToJson(snapshot.series))
    }

    private fun snapshotFromJson(payload: JSONObject): M3uCatalogSnapshot {
        return M3uCatalogSnapshot(
            channels = itemsFromJson(payload.optJSONArray("channels")),
            movies = itemsFromJson(payload.optJSONArray("movies")),
            series = itemsFromJson(payload.optJSONArray("series")),
        )
    }

    private fun itemsToJson(items: List<CatalogItem>): JSONArray {
        return JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject()
                        .put("stableId", item.stableId)
                        .put("title", item.title)
                        .put("subtitle", item.subtitle)
                        .put("description", item.description)
                        .put("imageUrl", item.imageUrl)
                        .put("kind", item.kind.name)
                        .put("group", item.group)
                        .put("badgeText", item.badgeText)
                        .put("channelNumber", item.channelNumber)
                        .put("streamOptions", JSONArray().apply {
                            item.streamOptions.forEach { option ->
                                put(
                                    JSONObject()
                                        .put("label", option.label)
                                        .put("url", option.url),
                                )
                            }
                        }),
                )
            }
        }
    }

    private fun itemsFromJson(array: JSONArray?): List<CatalogItem> {
        if (array == null) return emptyList()

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    CatalogItem(
                        stableId = item.optString("stableId"),
                        title = item.optString("title"),
                        subtitle = item.optString("subtitle"),
                        description = item.optString("description"),
                        imageUrl = item.optString("imageUrl"),
                        kind = runCatching { ContentKind.valueOf(item.optString("kind")) }.getOrDefault(ContentKind.CHANNEL),
                        group = item.optString("group"),
                        badgeText = item.optString("badgeText"),
                        channelNumber = item.takeIf { !it.isNull("channelNumber") }?.optInt("channelNumber"),
                        streamOptions = buildList {
                            val streamArray = item.optJSONArray("streamOptions") ?: JSONArray()
                            for (streamIndex in 0 until streamArray.length()) {
                                val stream = streamArray.optJSONObject(streamIndex) ?: continue
                                add(
                                    StreamOption(
                                        label = stream.optString("label"),
                                        url = stream.optString("url"),
                                    ),
                                )
                            }
                        },
                    ),
                )
            }
        }
    }

    private fun parsePlaylist(
        reader: BufferedReader,
        limits: ParseLimits?,
        stage: PlaylistLoadStage,
        progressLabel: String,
        totalBytes: Long,
    ): M3uCatalogSnapshot {
        val channels = mutableListOf<CatalogItem>()
        val movies = mutableListOf<CatalogItem>()
        val series = mutableListOf<CatalogItem>()
        var pendingInfo: ExtInfEntry? = null
        var channelNumber = 1
        var processedBytes = 0L
        var lastReportedPercent = -1

        while (true) {
            val rawLine = reader.readLine() ?: break
            processedBytes += rawLine.toByteArray().size + 1L
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> pendingInfo = parseExtInf(line)
                line.startsWith("#") || line.isBlank() -> Unit
                else -> {
                    val info = pendingInfo ?: continue
                    pendingInfo = null
                    val kind = classify(line)
                    val item = buildItemFromM3u(info, line, kind, channelNumber.takeIf { kind == ContentKind.CHANNEL })
                    when (kind) {
                        ContentKind.CHANNEL -> {
                            if (limits == null || channels.size < limits.channels) {
                                channels += item
                            }
                            channelNumber += 1
                        }
                        ContentKind.MOVIE -> if (limits == null || movies.size < limits.movies) movies += item
                        ContentKind.SERIES -> if (limits == null || series.size < limits.series) series += item
                        ContentKind.EVENT -> Unit
                    }

                    if (limits != null && channels.size >= limits.channels && movies.size >= limits.movies && series.size >= limits.series) {
                        reportProgress(
                            stage,
                            progressLabel,
                            100,
                            detail = buildParseDetail(channels.size, movies.size, series.size),
                        )
                        break
                    }
                }
            }

            val percent = if (totalBytes > 0L) {
                ((processedBytes.coerceAtMost(totalBytes) * 100L) / totalBytes).toInt()
            } else {
                0
            }
            if (percent >= lastReportedPercent + 3) {
                lastReportedPercent = percent
                reportProgress(
                    stage,
                    progressLabel,
                    percent,
                    detail = buildParseDetail(channels.size, movies.size, series.size),
                )
            }
        }

        Log.d(TAG, "Playlist parseada: ${channels.size} canales, ${movies.size} peliculas, ${series.size} series")
        reportProgress(
            stage,
            "$progressLabel completado",
            100,
            detail = buildParseDetail(channels.size, movies.size, series.size),
        )
        return M3uCatalogSnapshot(channels, movies, series)
    }

    private fun buildItemFromM3u(
        info: ExtInfEntry,
        url: String,
        kind: ContentKind,
        channelNumber: Int?,
    ): CatalogItem {
        val group = simplifyGroup(info.groupTitle)
        val streamId = url.substringBefore('?').substringAfterLast('/').substringBefore('.')
        val title = buildDisplayTitle(info, kind)
        return CatalogItem(
            stableId = "${kind.name.lowercase(Locale.US)}:$streamId",
            title = title,
            subtitle = group,
            description = group,
            imageUrl = normalizeImageUrl(info.logoUrl),
            kind = kind,
            group = group,
            badgeText = buildBadge(kind, group),
            channelNumber = channelNumber,
            streamOptions = listOf(StreamOption(defaultLabel(kind), url.replace("http://", "https://"))),
        )
    }

    private fun parseExtInf(line: String): ExtInfEntry {
        var inQuotes = false
        var commaIndex = -1
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuotes = !inQuotes
            if (c == ',' && !inQuotes) {
                commaIndex = i
                break
            }
        }

        val attributesPart = if (commaIndex != -1) line.substring(0, commaIndex) else line
        val displayName = if (commaIndex != -1) line.substring(commaIndex + 1).trim() else ""

        return ExtInfEntry(
            displayName = displayName,
            tvgName = extractAttribute(attributesPart, "tvg-name"),
            logoUrl = extractAttribute(attributesPart, "tvg-logo"),
            groupTitle = extractAttribute(attributesPart, "group-title"),
        )
    }

    private fun buildDisplayTitle(info: ExtInfEntry, kind: ContentKind): String {
        // Prefer displayName if present, as it is the real channel name. 
        // Fallback to tvgName if displayName is totally empty.
        val rawTitle = when {
            info.displayName.isNotBlank() -> info.displayName
            info.tvgName.isNotBlank() -> info.tvgName
            else -> "Canal Sin Nombre"
        }

        return when (kind) {
            ContentKind.CHANNEL -> cleanChannelTitle(rawTitle)
            ContentKind.MOVIE,
            ContentKind.SERIES,
            -> cleanVodTitle(rawTitle)
            ContentKind.EVENT -> cleanTitle(rawTitle)
        }
    }

    private fun cleanChannelTitle(value: String): String {
        return cleanTitle(value)
            .replace(Regex("^[A-Z]{2,4}\\|\\s*"), "")
            .replace(Regex("\\b(UHD|FHD|HD|SD|4K|HEVC|H265|50FPS|60FPS|VIP)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun cleanVodTitle(value: String): String {
        return cleanTitle(value)
            .replace(Regex("^[A-Z]{2,4}\\|\\s*"), "")
            .replace(Regex("\\b(UHD|FHD|HD|SD|4K|HEVC|H265)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractAttribute(source: String, attribute: String): String {
        val regex = Regex("$attribute=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        return regex.find(source)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun classify(url: String): ContentKind {
        return when {
            url.contains("/movie/", ignoreCase = true) -> ContentKind.MOVIE
            url.contains("/series/", ignoreCase = true) -> ContentKind.SERIES
            else -> ContentKind.CHANNEL
        }
    }

    private fun buildBadge(kind: ContentKind, group: String): String {
        return when (kind) {
            ContentKind.CHANNEL -> group.substringBefore('|').trim().ifBlank { "TV" }.take(8)
            ContentKind.MOVIE -> "CINE"
            ContentKind.SERIES -> "SERIE"
            ContentKind.EVENT -> ""
        }
    }

    private fun defaultLabel(kind: ContentKind): String {
        return when (kind) {
            ContentKind.CHANNEL -> "Directo"
            ContentKind.MOVIE -> "Reproducir"
            ContentKind.SERIES -> "Episodio"
            ContentKind.EVENT -> ""
        }
    }

    private fun downloadPlaylistToCache() {
        val credentials = currentCredentials()
        val tempFile = File(cacheFile.parentFile, "$CACHE_FILE_NAME.tmp")
        val connection = URL(buildPlaylistUrl(credentials.username, credentials.password)).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 30_000
            connection.readTimeout = 180_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/x-mpegURL,*/*")

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream ?: connection.inputStream
            if (statusCode !in 200..299) {
                val body = BufferedReader(InputStreamReader(BufferedInputStream(stream))).use(BufferedReader::readText)
                throw IllegalStateException("HTTP $statusCode: $body")
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
            BufferedInputStream(stream).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val percent = if (totalBytes > 0L) {
                            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            null
                        }
                        reportProgress(
                            PlaylistLoadStage.DOWNLOADING,
                            "Descargando lista IPTV",
                            percent,
                            detail = if (totalBytes > 0L) {
                                "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}"
                            } else {
                                formatBytes(downloadedBytes)
                            },
                        )
                    }
                }
            }

            Log.d(TAG, "Playlist descargada temporalmente. bytes=${tempFile.length()}")
            reportProgress(PlaylistLoadStage.SAVING_CACHE, "Guardando lista en cache", 100)

            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            tempFile.renameTo(cacheFile)
            Log.d(TAG, "Playlist guardada en cache: ${cacheFile.absolutePath} bytes=${cacheFile.length()}")
        } finally {
            connection.disconnect()
            if (tempFile.exists() && !cacheFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun cleanTitle(value: String): String = value.replace("  ", " ").trim()

    private fun simplifyGroup(group: String): String {
        if (group.isBlank()) return "Sin grupo"
        return group.replace(Regex("\\s+"), " ").trim()
    }

    private fun normalizeImageUrl(url: String): String {
        if (url.isBlank() || url == "null") return ""
        return url
            .replace("http://${BuildConfig.IPTV_BASE_URL.removePrefix("https://").removePrefix("http://")}", BuildConfig.IPTV_BASE_URL)
            .replace("http://image.tmdb.org", "https://image.tmdb.org")
    }

    private fun reportProgress(stage: PlaylistLoadStage, message: String, percent: Int?, detail: String? = null) {
        progressListener?.invoke(
            PlaylistLoadProgress(
                stage = stage,
                message = message,
                percent = percent,
                detail = detail,
            ),
        )
    }

    private fun buildSnapshotDetail(snapshot: M3uCatalogSnapshot): String {
        return "${snapshot.channels.size} canales · ${snapshot.movies.size} peliculas · ${snapshot.series.size} series"
    }

    private fun buildParseDetail(channels: Int, movies: Int, series: Int): String {
        return "$channels canales · $movies peliculas · $series series"
    }

    private fun currentCredentials(): StoredCredentials {
        val username = credentialStore.username()
        val password = credentialStore.password()
        check(username.isNotBlank() && password.isNotBlank()) {
            "No hay credenciales guardadas"
        }
        return StoredCredentials(username, password)
    }

    private fun buildPlaylistUrl(username: String, password: String): String {
        val baseUrl = BuildConfig.IPTV_BASE_URL.removeSuffix("/")
        return "$baseUrl/get.php?username=$username&password=$password"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024f
        val mb = kb / 1024f
        return when {
            mb >= 1f -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1f -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun saveLastUpdated(value: Long) {
        preferences.edit().putLong(KEY_LAST_UPDATED, value).apply()
    }

    private val preferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val cacheFile: File by lazy {
        File(context.filesDir, CACHE_FILE_NAME)
    }

    private val fullSnapshotFile: File by lazy {
        File(context.filesDir, FULL_SNAPSHOT_FILE_NAME)
    }

    private val summarySnapshotFile: File by lazy {
        File(context.filesDir, SUMMARY_SNAPSHOT_FILE_NAME)
    }

    companion object {
        private const val TAG = "M3uCatalogStore"
        private const val PREFS_NAME = "m3u_catalog_store"
        private const val KEY_LAST_UPDATED = "last_updated"
        private const val CACHE_FILE_NAME = "playlist_cache.m3u"
        private const val FULL_SNAPSHOT_FILE_NAME = "playlist_snapshot_full.json"
        private const val SUMMARY_SNAPSHOT_FILE_NAME = "playlist_snapshot_summary.json"
        private const val USER_AGENT = "WalacTV AndroidTV"
        private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
        private data class StoredCredentials(
            val username: String,
            val password: String,
        )

        @Volatile
        private var fullMemorySnapshot: M3uCatalogSnapshot? = null

        @Volatile
        private var summaryMemorySnapshot: M3uCatalogSnapshot? = null

        private val HOME_LIMITS = ParseLimits(
            channels = 100,
            movies = 50,
            series = 50,
        )
    }
}

data class PlaylistLoadProgress(
    val stage: PlaylistLoadStage,
    val message: String,
    val percent: Int? = null,
    val detail: String? = null,
)

enum class PlaylistLoadStage {
    CHECKING_CACHE,
    DOWNLOADING,
    PARSING_SUMMARY,
    PARSING_FULL,
    SAVING_CACHE,
    READY,
}

data class M3uCatalogSnapshot(
    val channels: List<CatalogItem>,
    val movies: List<CatalogItem>,
    val series: List<CatalogItem>,
)

private data class ExtInfEntry(
    val displayName: String,
    val tvgName: String,
    val logoUrl: String,
    val groupTitle: String,
)

private data class ParseLimits(
    val channels: Int,
    val movies: Int,
    val series: Int,
)
