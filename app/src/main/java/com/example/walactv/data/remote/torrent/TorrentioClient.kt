package com.example.walactv.data.remote.torrent

import android.util.Log
import com.example.walactv.data.model.StreamOption
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Cliente Torrentio directo (sin pasar por iptv-api).
 * Replica la logica de WalacTV Desktop (src/api/client.ts) y de
 * iptv-api TorrentioService: normalizacion, idioma y cache efimera.
 */
object TorrentioClient {

    private const val TAG = "TorrentioClient"
    private const val BASE_URL = "https://torrentio.strem.fun"
    private const val PROVIDERS = "wolfmax4k,comando,yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,magnetdl,torrentproject,ibit,filelist"
    private const val LANGUAGES = "spanish,english"
    private const val TIMEOUT_SECONDS = 15L
    private const val CACHE_TTL_MS = 60_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private data class CacheEntry(val expiresAt: Long, val items: List<StreamOption>)
    private val cache = mutableMapOf<String, CacheEntry>()

    private val IMDB_RE = Regex("^tt\\d+$", RegexOption.IGNORE_CASE)
    private val INFO_HASH_RE = Regex("^[a-fA-F0-9]{40}$")
    private val SEEDERS_RE = Regex("[\uD83D\uDC64]\\s*([\\d,.]+)")
    private val SIZE_RE = Regex("\uD83D\uDCBE\\s*([\\d,.]+)\\s*(KB|MB|GB|TB)", RegexOption.IGNORE_CASE)
    private val QUALITY_RE = Regex("\\b(4k|2160p|1080p|720p|480p)\\b", RegexOption.IGNORE_CASE)

    // Bandera de España real (E+S). Antes tenia S+A (Arabia Saudita) y la
    // deteccion de ES por bandera nunca matcheaba.
    private val LANGUAGE_FLAGS = mapOf(
        "\uD83C\uDDEA\uD83C\uDDF8" to "ES",
        "\uD83C\uDDEC\uD83C\uDDE7" to "EN",
        "\uD83C\uDDEF\uD83C\uDDF5" to "JP",
    )
    private val EXCLUDED_MARKERS = listOf("\uD83C\uDDF2\uD83C\uDDFD", "latino")
    private val FOREIGN_FLAGS = listOf(
        "\uD83C\uDDEE\uD83C\uDDF9", "\uD83C\uDDF5\uD83C\uDDF9", "\uD83C\uDDF7\uD83C\uDDFA",
        "\uD83C\uDDEB\uD83C\uDDF7", "\uD83C\uDDE9\uD83C\uDDEA", "\uD83C\uDDF5\uD83C\uDDF1",
        "\uD83C\uDDE8\uD83C\uDDF3", "\uD83C\uDDEF\uD83C\uDDF5",
    )

    fun isImdbId(value: String?): Boolean = value != null && IMDB_RE.matches(value.trim())

    suspend fun movieStreams(imdbId: String): List<StreamOption> {
        if (!isImdbId(imdbId)) return emptyList()
        return fetchStreams("movie", imdbId.trim())
    }

    suspend fun episodeStreams(imdbId: String, season: Int, episode: Int): List<StreamOption> {
        if (!isImdbId(imdbId)) return emptyList()
        if (season < 0 || episode < 0) return emptyList()
        return fetchStreams("series", "${imdbId.trim()}:$season:$episode")
    }

    private suspend fun fetchStreams(contentType: String, contentId: String): List<StreamOption> =
        withContext(Dispatchers.IO) {
            val config = buildConfig()
            val cacheKey = "$config/$contentType/$contentId"
            val now = System.currentTimeMillis()
            cache[cacheKey]?.let { entry ->
                if (entry.expiresAt > now) {
                    Log.d(TAG, "cache hit: $cacheKey (${entry.items.size} streams)")
                    return@withContext entry.items
                }
                cache.remove(cacheKey)
            }

            val url = "$BASE_URL/$config/stream/$contentType/$contentId.json"
            Log.d(TAG, "lookup $url")
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "WalacTV-Android/Torrentio")
                .build()

            val body = try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "HTTP ${response.code} for $contentType/$contentId")
                        return@withContext emptyList()
                    }
                    response.body?.string()
                }
            } catch (e: Exception) {
                Log.e(TAG, "network error for $contentType/$contentId: ${e.message}")
                return@withContext emptyList()
            } ?: return@withContext emptyList()

            val items = runCatching {
                parseStreams(body)
            }.getOrElse {
                Log.e(TAG, "invalid payload for $contentType/$contentId: ${it.message}")
                emptyList()
            }

            cache.entries.removeAll { it.value.expiresAt <= now }
            cache[cacheKey] = CacheEntry(now + CACHE_TTL_MS, items)
            Log.d(TAG, "streams=${items.size} for $contentType/$contentId")
            items
        }

    private fun buildConfig(): String = "providers=$PROVIDERS|language=$LANGUAGES"

    private fun parseStreams(body: String): List<StreamOption> {
        val root = JsonParser.parseString(body)
        val streams = root.asJsonObject.get("streams")?.asJsonArray ?: return emptyList()
        return streams.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            normalize(
                infoHashRaw = obj.get("infoHash")?.asString,
                name = obj.get("name")?.asString,
                title = obj.get("title")?.asString,
                fileIdx = obj.get("fileIdx")?.takeIf { it.isJsonPrimitive }?.asInt,
            )
        }
    }

    internal fun normalize(infoHashRaw: String?, name: String?, title: String?, fileIdx: Int?): StreamOption? {
        val infoHash = infoHashRaw?.trim() ?: return null
        if (!INFO_HASH_RE.matches(infoHash)) return null
        val fullTitle = title?.trim().orEmpty()
        val languages = detectLanguages(fullTitle)
        if (languages.isEmpty()) return null
        val qualityMatch = QUALITY_RE.find(name.orEmpty()) ?: QUALITY_RE.find(fullTitle)
        val sizeMatch = SIZE_RE.find(fullTitle)
        val seedersMatch = SEEDERS_RE.find(fullTitle)
        val label = providerLabel(fullTitle)
        return StreamOption(
            label = label,
            url = "magnet:?xt=urn:btih:${infoHash.lowercase()}",
            providerId = infoHash.lowercase(),
            language = languages.first(),
            languages = languages,
            quality = qualityMatch?.value?.uppercase(),
            infoHash = infoHash.lowercase(),
            fileIdx = fileIdx,
            seeders = seedersMatch?.groupValues?.get(1)?.replace(",", "")?.replace(".", "")?.toIntOrNull(),
            sizeBytes = sizeBytes(sizeMatch),
            torrentTitle = fullTitle.ifBlank { null },
        )
    }

    /**
     * Idiomas declarados en el titulo Torrentio (banderas, [ES]/[EN] o
     * palabras clave). Espejo de detect_languages del scrapper. Una release
     * dual "🇬🇧 / 🇪🇸" devuelve [EN, ES]. Lista vacia = descartar el stream.
     */
    private fun detectLanguages(title: String): List<String> {
        if (EXCLUDED_MARKERS.any { title.contains(it, ignoreCase = true) }) return emptyList()
        val knownFlags = LANGUAGE_FLAGS.filterKeys { title.contains(it) }.values.distinct()
        if (knownFlags.isNotEmpty()) return knownFlags
        if (FOREIGN_FLAGS.any { title.contains(it) }) return emptyList()
        val bracketed = Regex("\\[(ES|EN|JP)\\]", RegexOption.IGNORE_CASE)
            .findAll(title).map { it.groupValues[1].uppercase() }.distinct().toList()
        if (bracketed.isNotEmpty()) return bracketed
        val lowered = title.lowercase()
        val found = mutableListOf<String>()
        if (Regex("\\b(spanish|castellano|español)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lowered)) found += "ES"
        if (Regex("\\benglish\\b", RegexOption.IGNORE_CASE).containsMatchIn(lowered)) found += "EN"
        if (found.isNotEmpty()) return found
        return listOf("EN")
    }

    private fun providerLabel(title: String): String {
        val marker = "\u2699\uFE0F"
        val index = title.indexOf(marker)
        if (index < 0) return "Torrentio"
        val after = title.substring(index + marker.length).trim()
        val line = after.lineSequence().firstOrNull()?.trim().orEmpty()
        return line.ifBlank { "Torrentio" }
    }

    private fun sizeBytes(match: MatchResult?): Long? {
        match ?: return null
        val value = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].uppercase()) {
            "KB" -> 1024L
            "MB" -> 1024L * 1024
            "GB" -> 1024L * 1024 * 1024
            "TB" -> 1024L * 1024 * 1024 * 1024
            else -> return null
        }
        return (value * multiplier).toLong()
    }
}
