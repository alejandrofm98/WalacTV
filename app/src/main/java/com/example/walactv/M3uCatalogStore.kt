package com.example.walactv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class M3uCatalogStore(private val context: Context) {

    private val credentialStore = CredentialStore(context)
    private val providerHttpBaseUrl = BuildConfig.IPTV_BASE_URL.removePrefix("https://").removePrefix("http://")
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

    suspend fun refreshNow(): M3uCatalogSnapshot = withContext(Dispatchers.IO) {
        Log.d(TAG, "Refrescando playlist M3U ahora")
        reportProgress(PlaylistLoadStage.DOWNLOADING, "Descargando lista IPTV", 0)
        downloadPlaylistToCache()
        saveLastUpdated(System.currentTimeMillis())
        fullMemorySnapshot = null
        deletePersistedSnapshots()

        val fullSnapshot = loadFromCache().also {
            fullMemorySnapshot = it
            persistSnapshot(fullSnapshotFile, it)
        }

        fullSnapshot
    }

    fun clearAllCache() {
        fullMemorySnapshot = null
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
                stage = PlaylistLoadStage.PARSING_FULL,
                progressLabel = "Parseando lista completa",
                totalBytes = cacheFile.length(),
            )
        }
    }

    private fun loadPersistedSnapshot(file: File): M3uCatalogSnapshot? {
        // Load persisted binary snapshot regardless of TTL expiration.
        // Stale data is shown immediately; background refresh handles re-download.
        if (!file.exists()) return null

        return runCatching {
            DataInputStream(file.inputStream().buffered()).use(::readSnapshot)
        }.onFailure {
            Log.w(TAG, "No se pudo leer snapshot persistido ${file.name}", it)
            file.delete()
        }.getOrNull()
    }

    private fun persistSnapshot(file: File, snapshot: M3uCatalogSnapshot) {
        runCatching {
            DataOutputStream(file.outputStream().buffered()).use { writeSnapshot(it, snapshot) }
        }.onFailure {
            Log.w(TAG, "No se pudo guardar snapshot persistido ${file.name}", it)
        }
    }

    private fun deletePersistedSnapshots() {
        fullSnapshotFile.delete()
    }

    private fun writeSnapshot(output: DataOutputStream, snapshot: M3uCatalogSnapshot) {
        output.writeInt(SNAPSHOT_VERSION)
        writeItems(output, snapshot.channels)
        writeItems(output, snapshot.movies)
        writeItems(output, snapshot.series)
        output.flush()
    }

    private fun readSnapshot(input: DataInputStream): M3uCatalogSnapshot {
        val version = input.readInt()
        check(version == SNAPSHOT_VERSION) { "Version de snapshot no compatible: $version" }
        return M3uCatalogSnapshot(
            channels = readItems(input),
            movies = readItems(input),
            series = readItems(input),
        )
    }

    private fun writeItems(output: DataOutputStream, items: List<CatalogItem>) {
        output.writeInt(items.size)
        items.forEach { item ->
            output.writeUTF(item.stableId)
            output.writeBoolean(item.providerId != null)
            item.providerId?.let(output::writeUTF)
            output.writeUTF(item.title)
            output.writeUTF(item.subtitle)
            output.writeUTF(item.description)
            output.writeUTF(item.imageUrl)
            output.writeInt(item.kind.ordinal)
            output.writeUTF(item.group)
            output.writeUTF(item.badgeText)
            output.writeBoolean(item.channelNumber != null)
            item.channelNumber?.let(output::writeInt)
            output.writeBoolean(item.languageLabel != null)
            item.languageLabel?.let(output::writeUTF)
            output.writeBoolean(item.normalizedTitle != null)
            item.normalizedTitle?.let(output::writeUTF)
            output.writeBoolean(item.normalizedGroup != null)
            item.normalizedGroup?.let(output::writeUTF)
            output.writeBoolean(item.seriesName != null)
            item.seriesName?.let(output::writeUTF)
            output.writeBoolean(item.seasonNumber != null)
            item.seasonNumber?.let(output::writeInt)
            output.writeBoolean(item.episodeNumber != null)
            item.episodeNumber?.let(output::writeInt)
            output.writeInt(item.streamOptions.size)
            item.streamOptions.forEach { option ->
                output.writeUTF(option.label)
                output.writeUTF(option.url)
            }
        }
    }

    private fun readItems(input: DataInputStream): List<CatalogItem> {
        val count = input.readInt()
        return List(count) {
            val stableId = input.readUTF()
            val hasProviderId = input.readBoolean()
            val providerId = if (hasProviderId) input.readUTF() else null
            val title = input.readUTF()
            val subtitle = input.readUTF()
            val description = input.readUTF()
            val imageUrl = input.readUTF()
            val kind = ContentKind.entries[input.readInt()]
            val group = input.readUTF()
            val badgeText = input.readUTF()
            val hasChannelNumber = input.readBoolean()
            val channelNumber = if (hasChannelNumber) input.readInt() else null
            val hasLanguageLabel = input.readBoolean()
            val languageLabel = if (hasLanguageLabel) input.readUTF() else null
            val hasNormalizedTitle = input.readBoolean()
            val normalizedTitle = if (hasNormalizedTitle) input.readUTF() else null
            val hasNormalizedGroup = input.readBoolean()
            val normalizedGroup = if (hasNormalizedGroup) input.readUTF() else null
            val hasSeriesName = input.readBoolean()
            val seriesName = if (hasSeriesName) input.readUTF() else null
            val hasSeasonNumber = input.readBoolean()
            val seasonNumber = if (hasSeasonNumber) input.readInt() else null
            val hasEpisodeNumber = input.readBoolean()
            val episodeNumber = if (hasEpisodeNumber) input.readInt() else null
            val streamCount = input.readInt()
            CatalogItem(
                stableId = stableId,
                providerId = providerId,
                title = title,
                subtitle = subtitle,
                description = description,
                imageUrl = imageUrl,
                kind = kind,
                group = group,
                badgeText = badgeText,
                channelNumber = channelNumber,
                languageLabel = languageLabel,
                normalizedTitle = normalizedTitle,
                normalizedGroup = normalizedGroup,
                seriesName = seriesName,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                streamOptions = List(streamCount) {
                    StreamOption(
                        label = input.readUTF(),
                        url = input.readUTF(),
                    )
                },
            )
        }
    }

    private fun parsePlaylist(
        reader: BufferedReader,
        stage: PlaylistLoadStage,
        progressLabel: String,
        totalBytes: Long,
    ): M3uCatalogSnapshot {
        val startedAt = System.currentTimeMillis()
        val channels = ArrayList<CatalogItem>(4096)
        val movies = ArrayList<CatalogItem>(512)
        val series = ArrayList<CatalogItem>(512)
        var pendingInfo: ExtInfEntry? = null
        var channelNumber = 1
        var processedBytes = 0L
        var lastReportedPercent = -1

        while (true) {
            val rawLine = reader.readLine() ?: break
            processedBytes += rawLine.length + 1L
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
                            channels += item
                            channelNumber += 1
                        }
                        ContentKind.MOVIE -> movies += item
                        ContentKind.SERIES -> series += item
                        ContentKind.EVENT -> Unit
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
                    elapsedMs = System.currentTimeMillis() - startedAt,
                )
            }
        }

        Log.d(TAG, "Playlist parseada: ${channels.size} canales, ${movies.size} peliculas, ${series.size} series")
        reportProgress(
            stage,
            "$progressLabel completado",
            100,
            detail = buildParseDetail(channels.size, movies.size, series.size),
            elapsedMs = System.currentTimeMillis() - startedAt,
        )
        return M3uCatalogSnapshot(channels, movies, series)
    }

    private fun buildItemFromM3u(
        info: ExtInfEntry,
        url: String,
        kind: ContentKind,
        channelNumber: Int?,
    ): CatalogItem {
        val normalized = parseNormalizedMetadata(
            kind = kind,
            groupTitle = info.groupTitle,
            tvgName = info.tvgName,
            displayName = info.displayName,
            walacLanguage = info.walacLanguage,
            walacNameNormalized = info.walacNameNormalized,
            walacGroupNormalized = info.walacGroupNormalized,
            walacSeriesNameNormalized = info.walacSeriesNameNormalized,
        )
        val group = simplifyGroup(normalized.groupTitle)
        val streamId = url.substringBefore('?').substringAfterLast('/').substringBefore('.')
        val title = buildDisplayTitle(info, kind, normalized.displayTitle)
        val parsedSeriesMetadata = parseSeriesMetadata(title, kind)
        val seriesMetadata = if (kind == ContentKind.SERIES && !normalized.seriesName.isNullOrBlank()) {
            parsedSeriesMetadata.copy(seriesName = normalized.seriesName)
        } else {
            parsedSeriesMetadata
        }
        return CatalogItem(
            stableId = "${kind.name.lowercase(Locale.US)}:$streamId",
            providerId = streamId,
            title = title,
            subtitle = group,
            description = group,
            imageUrl = normalizeImageUrl(info.logoUrl),
            kind = kind,
            group = group,
            badgeText = buildBadge(kind, group),
            channelNumber = channelNumber,
            languageLabel = normalized.languageLabel,
            normalizedTitle = normalized.displayTitle,
            normalizedGroup = normalized.groupTitle,
            seriesName = seriesMetadata.seriesName,
            seasonNumber = seriesMetadata.seasonNumber,
            episodeNumber = seriesMetadata.episodeNumber,
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
            walacLanguage = extractAttribute(attributesPart, "walac-language"),
            walacNameNormalized = extractAttribute(attributesPart, "walac-name-normalized"),
            walacGroupNormalized = extractAttribute(attributesPart, "walac-group-normalized"),
            walacSeriesNameNormalized = extractAttribute(attributesPart, "walac-series-name-normalized"),
        )
    }

    private fun buildDisplayTitle(info: ExtInfEntry, kind: ContentKind, preferredTitle: String): String {
        // Prefer displayName if present, as it is the real channel name. 
        // Fallback to tvgName if displayName is totally empty.
        val rawTitle = when {
            preferredTitle.isNotBlank() -> preferredTitle
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
            .replace(CHANNEL_PREFIX_REGEX, "")
            .replace(CHANNEL_QUALITY_REGEX, "")
            .replace(MULTIPLE_SPACES_REGEX, " ")
            .trim()
    }

    private fun cleanVodTitle(value: String): String {
        return cleanTitle(value)
            .replace(CHANNEL_PREFIX_REGEX, "")
            .replace(VOD_QUALITY_REGEX, "")
            .replace(MULTIPLE_SPACES_REGEX, " ")
            .trim()
    }

    private fun extractAttribute(source: String, attribute: String): String {
        val key = "$attribute=\""
        val start = source.indexOf(key, ignoreCase = true)
        if (start == -1) return ""
        val valueStart = start + key.length
        val valueEnd = source.indexOf('"', valueStart)
        if (valueEnd == -1) return ""
        return source.substring(valueStart, valueEnd)
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
            ContentKind.MOVIE -> ""
            ContentKind.SERIES -> ""
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
                            elapsedMs = null,
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
        return group.replace(MULTIPLE_SPACES_REGEX, " ").trim()
    }

    private fun normalizeImageUrl(url: String): String {
        if (url.isBlank() || url == "null") return ""
        return url
            .replace("http://$providerHttpBaseUrl", BuildConfig.IPTV_BASE_URL)
            .replace("http://image.tmdb.org", "https://image.tmdb.org")
    }

    private fun reportProgress(
        stage: PlaylistLoadStage,
        message: String,
        percent: Int?,
        detail: String? = null,
        elapsedMs: Long? = null,
    ) {
        progressListener?.invoke(
            PlaylistLoadProgress(
                stage = stage,
                message = message,
                percent = percent,
                detail = detail,
                elapsedMs = elapsedMs,
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

    companion object {
        private const val TAG = "M3uCatalogStore"
        private val CHANNEL_PREFIX_REGEX = Regex("^[A-Z]{2,4}\\|\\s*")
        private val CHANNEL_QUALITY_REGEX = Regex("\\b(UHD|FHD|HD|SD|4K|HEVC|H265|50FPS|60FPS|VIP)\\b", RegexOption.IGNORE_CASE)
        private val VOD_QUALITY_REGEX = Regex("\\b(UHD|FHD|HD|SD|4K|HEVC|H265|LQ|HQ)\\b", RegexOption.IGNORE_CASE)
        private val MULTIPLE_SPACES_REGEX = Regex("\\s+")
        private const val PREFS_NAME = "m3u_catalog_store"
        private const val KEY_LAST_UPDATED = "last_updated"
        private const val CACHE_FILE_NAME = "playlist_cache.m3u"
        private const val FULL_SNAPSHOT_FILE_NAME = "playlist_snapshot_full.bin"
        private const val SNAPSHOT_VERSION = 3
        private const val USER_AGENT = "WalacTV AndroidTV"
        private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
        private data class StoredCredentials(
            val username: String,
            val password: String,
        )

        @Volatile
        private var fullMemorySnapshot: M3uCatalogSnapshot? = null
    }
}

data class PlaylistLoadProgress(
    val stage: PlaylistLoadStage,
    val message: String,
    val percent: Int? = null,
    val detail: String? = null,
    val elapsedMs: Long? = null,
)

enum class PlaylistLoadStage {
    CHECKING_CACHE,
    DOWNLOADING,
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
    val walacLanguage: String,
    val walacNameNormalized: String,
    val walacGroupNormalized: String,
    val walacSeriesNameNormalized: String,
)
