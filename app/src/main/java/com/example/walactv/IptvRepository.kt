package com.example.walactv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class IptvRepository(context: Context) {

    private val appContext = context.applicationContext
    private val m3uCatalogStore = M3uCatalogStore(appContext)
    private val credentialStore = CredentialStore(appContext)
    private var accessToken: String? = null

    fun setPlaylistProgressListener(listener: ((PlaylistLoadProgress) -> Unit)?) {
        m3uCatalogStore.progressListener = listener
    }

    fun hasStoredCredentials(): Boolean = credentialStore.hasCredentials()

    fun currentUsername(): String = credentialStore.username()

    suspend fun signIn(username: String, password: String) {
        val trimmedUsername = username.trim()
        val trimmedPassword = password.trim()
        require(trimmedUsername.isNotBlank() && trimmedPassword.isNotBlank()) {
            "Introduce usuario y contrasena"
        }
        require(isBaseUrlConfigured()) {
            "Falta configurar walactv.iptvBaseUrl en local.properties"
        }

        Log.d(TAG, "Intentando login en ${BuildConfig.IPTV_BASE_URL} con usuario ${maskUsername(trimmedUsername)}")

        try {
            val response = postForm(
                url = "${BuildConfig.IPTV_BASE_URL}/api/auth/login",
                body = buildFormBody("username" to trimmedUsername, "password" to trimmedPassword),
            )
            accessToken = response.getString("access_token")
            credentialStore.save(trimmedUsername, trimmedPassword)
            clearHomeMemoryCache()
            m3uCatalogStore.clearAllCache()
            Log.d(TAG, "Login correcto para ${maskUsername(trimmedUsername)}")
        } catch (exception: Exception) {
            Log.e(TAG, "Fallo en login para ${maskUsername(trimmedUsername)}", exception)
            throw IllegalStateException(buildLoginErrorMessage(exception), exception)
        }
    }

    fun signOut() {
        accessToken = null
        clearHomeMemoryCache()
        credentialStore.clear()
        m3uCatalogStore.clearAllCache()
    }

    suspend fun loadEventsOnly(): HomeCatalog = withContext(Dispatchers.IO) {
        coroutineScope {
            val token = getAccessToken()
            val eventSectionsDeferred = async { safeSectionLoad("eventos") { fetchEventSections(token) } }
            val playlistDeferred = async { safePlaylistLoad(forceRefresh = false, useSummaryPlaylist = true) }

            val eventSections = eventSectionsDeferred.await()
            val playlist = playlistDeferred.await()
            val resolvedEventSections = reconcileEventSections(eventSections, playlist.channels)

            HomeCatalog(
                sections = resolvedEventSections,
                searchableItems = resolvedEventSections.flatMap(BrowseSection::items).distinctBy(CatalogItem::stableId),
            )
        }
    }

    suspend fun loadHomeCatalog(
        forceRefreshPlaylist: Boolean = false,
        useSummaryPlaylist: Boolean = false,
    ): HomeCatalog = withContext(Dispatchers.IO) {
        if (!forceRefreshPlaylist) {
            val cached = if (useSummaryPlaylist) memoryHomeSummaryCatalog else memoryHomeCatalog
            cached?.let { return@withContext it }
        }

        coroutineScope {
            val token = getAccessToken()

            val eventSectionsDeferred = async { safeSectionLoad("eventos") { fetchEventSections(token) } }
            val playlistDeferred = async { safePlaylistLoad(forceRefreshPlaylist, useSummaryPlaylist) }

            val eventSections = eventSectionsDeferred.await()
            val playlist = playlistDeferred.await()
            val resolvedEventSections = reconcileEventSections(eventSections, playlist.channels)

            val sections = buildList {
                addAll(resolvedEventSections)
                if (playlist.channels.isNotEmpty()) add(BrowseSection("Canales", playlist.channels.take(FEATURED_CHANNELS)))
                if (playlist.movies.isNotEmpty()) add(BrowseSection("Peliculas", playlist.movies.take(FEATURED_MOVIES)))
                if (playlist.series.isNotEmpty()) add(BrowseSection("Series", playlist.series.take(FEATURED_SERIES)))
            }

            HomeCatalog(
                sections = sections,
                searchableItems = buildList {
                    addAll(resolvedEventSections.flatMap(BrowseSection::items))
                    addAll(playlist.channels)
                    addAll(playlist.movies)
                    addAll(playlist.series)
                }.distinctBy(CatalogItem::stableId),
            ).also {
                if (useSummaryPlaylist) {
                    memoryHomeSummaryCatalog = it
                } else {
                    memoryHomeCatalog = it
                }
            }
        }
    }

    suspend fun refreshPlaylistNow(): Long {
        m3uCatalogStore.refreshNow()
        memoryHomeCatalog = null
        memoryHomeSummaryCatalog = null
        return m3uCatalogStore.getLastUpdatedMillis()
    }

    fun getLastPlaylistUpdateMillis(): Long = m3uCatalogStore.getLastUpdatedMillis()

    fun shouldRefreshPlaylistInBackground(): Boolean = m3uCatalogStore.needsBackgroundRefresh()

    fun hasPlaylistCache(): Boolean = m3uCatalogStore.hasCache()

    fun getPlaylistCacheSizeBytes(): Long = m3uCatalogStore.getCacheSizeBytes()

    fun clearHomeMemoryCache() {
        memoryHomeCatalog = null
        memoryHomeSummaryCatalog = null
    }

    suspend fun refreshPlaylistInBackground() {
        m3uCatalogStore.refreshNow()
        memoryHomeCatalog = null
        memoryHomeSummaryCatalog = null
    }

    suspend fun resolveEventItem(eventItem: CatalogItem): CatalogItem = withContext(Dispatchers.IO) {
        if (eventItem.kind != ContentKind.EVENT) return@withContext eventItem

        val playlist = safePlaylistLoad(forceRefresh = false, useSummaryPlaylist = false)
        reconcileEventItem(eventItem, playlist.channels)
    }

    private suspend fun fetchEventSections(token: String): List<BrowseSection> {
        val today = DATE_FORMATTER.format(Date())
        val payload = getJsonObject("${BuildConfig.IPTV_BASE_URL}/api/calendar/$today", token)
        val eventsArray = payload.optJSONArray("eventos") ?: JSONArray()
        if (eventsArray.length() == 0) return emptyList()

        val rawEvents = mutableListOf<RawCalendarEvent>()
        for (index in 0 until eventsArray.length()) {
            val eventObject = eventsArray.getJSONObject(index)
            val resolvedChannels = eventObject.optJSONArray("canales_resueltos") ?: JSONArray()
            val channelRefs = mutableListOf<ChannelRef>()

            for (channelIndex in 0 until resolvedChannels.length()) {
                val channelObject = resolvedChannels.getJSONObject(channelIndex)
                val channelId = channelObject.optString("channel_id")
                if (channelId.isBlank()) continue
                channelRefs += ChannelRef(
                    channelId = channelId,
                    displayName = channelObject.optString("display_name"),
                    quality = channelObject.optString("quality"),
                    logoUrl = normalizeImageUrl(channelObject.optString("logo")),
                )
            }

            rawEvents += RawCalendarEvent(
                id = eventObject.optString("id"),
                title = eventObject.optString("equipos"),
                competition = eventObject.optString("competicion"),
                category = eventObject.optString("categoria"),
                time = eventObject.optString("hora"),
                channels = channelRefs,
            )
        }

        val items = rawEvents.mapNotNull(::buildEventItem).sortedBy(::eventSortScore)
        if (items.isEmpty()) return emptyList()

        return buildList {
            add(BrowseSection("Eventos de hoy", items))
            items.groupBy { it.group.ifBlank { "Agenda" } }
                .entries
                .sortedByDescending { it.value.size }
                .forEach { entry -> add(BrowseSection("Eventos · ${entry.key}", entry.value)) }
        }
    }

    private fun buildEventItem(event: RawCalendarEvent): CatalogItem? {
        val options = event.channels.mapNotNull { channel ->
            val url = buildChannelUrl(channel.channelId)
            if (url.isBlank()) null else StreamOption(
                label = listOf(channel.displayName, channel.quality).filter { it.isNotBlank() }.joinToString(" · "),
                url = url,
            )
        }.distinctBy(StreamOption::url)

        if (options.isEmpty()) return null

        return CatalogItem(
            stableId = event.id,
            title = event.title,
            subtitle = listOf(event.time, event.competition).filter { it.isNotBlank() }.joinToString("  •  "),
            description = event.channels.joinToString(" · ") { it.displayName },
            imageUrl = event.channels.firstOrNull()?.logoUrl.orEmpty(),
            kind = ContentKind.EVENT,
            group = event.category.ifBlank { "Agenda" },
            badgeText = event.time,
            streamOptions = options,
        )
    }

    private fun buildChannelUrl(channelId: String): String {
        val credentials = requireCredentials()
        return if (channelId.isBlank()) "" else "${BuildConfig.IPTV_BASE_URL}/${credentials.username}/${credentials.password}/$channelId"
    }

    private fun reconcileEventSections(
        eventSections: List<BrowseSection>,
        channels: List<CatalogItem>,
    ): List<BrowseSection> {
        if (eventSections.isEmpty() || channels.isEmpty()) return eventSections

        val channelsByStreamId = channels.associateBy(::extractChannelStreamId)
        val channelsByName = channels.associateBy { normalizeChannelName(it.title) }

        return eventSections.map { section ->
            section.copy(
                items = section.items.map { item -> reconcileEventItem(item, channelsByStreamId, channelsByName) }
                    .filter { it.kind != ContentKind.EVENT || it.streamOptions.isNotEmpty() },
            )
        }
    }

    private fun reconcileEventItem(item: CatalogItem, channels: List<CatalogItem>): CatalogItem {
        if (item.kind != ContentKind.EVENT || channels.isEmpty()) return item
        val channelsByStreamId = channels.associateBy(::extractChannelStreamId)
        val channelsByName = channels.associateBy { normalizeChannelName(it.title) }
        return reconcileEventItem(item, channelsByStreamId, channelsByName)
    }

    private fun reconcileEventItem(
        item: CatalogItem,
        channelsByStreamId: Map<String, CatalogItem>,
        channelsByName: Map<String, CatalogItem>,
    ): CatalogItem {
        if (item.kind != ContentKind.EVENT) return item

        val resolvedChannels = item.streamOptions.mapNotNull { option ->
            findMatchingChannelForOption(option, channelsByStreamId, channelsByName)
        }.distinctBy(CatalogItem::stableId)

        if (resolvedChannels.isEmpty()) {
            return item.copy(streamOptions = emptyList())
        }

        val resolvedOptions = resolvedChannels.map { channel ->
            StreamOption(
                label = channel.title,
                url = channel.streamOptions.firstOrNull()?.url.orEmpty(),
            )
        }.filter { it.url.isNotBlank() }

        return item.copy(
            imageUrl = resolvedChannels.firstOrNull()?.imageUrl?.takeIf { it.isNotBlank() } ?: item.imageUrl,
            description = resolvedChannels.joinToString(" · ") { it.title },
            streamOptions = resolvedOptions,
        )
    }

    private fun findMatchingChannelForOption(
        option: StreamOption,
        channelsByStreamId: Map<String, CatalogItem>,
        channelsByName: Map<String, CatalogItem>,
    ): CatalogItem? {
        val optionName = normalizeChannelName(option.label.substringBefore(" · "))
        val optionStreamId = extractStreamIdFromUrl(option.url)

        channelsByStreamId[optionStreamId]?.let { return it }
        channelsByName[optionName]?.let { return it }

        return channelsByName.entries.firstOrNull { (name, _) ->
            name.isNotBlank() && optionName.isNotBlank() && (name.contains(optionName) || optionName.contains(name))
        }?.value
    }

    private fun extractChannelStreamId(item: CatalogItem): String {
        return item.streamOptions.firstOrNull()?.url?.let(::extractStreamIdFromUrl)
            ?: item.stableId.substringAfter(':', "")
    }

    private fun extractStreamIdFromUrl(url: String): String {
        return url.substringBefore('?').substringAfterLast('/').substringBefore('.')
    }

    private fun normalizeChannelName(name: String): String {
        return name.lowercase(Locale.ROOT)
            .replace(Regex("\\b(hd|fhd|uhd|sd|4k)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private suspend fun getAccessToken(): String {
        accessToken?.let { return it }
        val credentials = requireCredentials()
        val response = postForm(
            url = "${BuildConfig.IPTV_BASE_URL}/api/auth/login",
            body = buildFormBody("username" to credentials.username, "password" to credentials.password),
        )
        return response.getString("access_token").also { accessToken = it }
    }

    private fun requireCredentials(): StoredCredentials {
        val username = credentialStore.username()
        val password = credentialStore.password()
        check(username.isNotBlank() && password.isNotBlank()) {
            "No hay sesion iniciada"
        }
        return StoredCredentials(username, password)
    }

    private fun isBaseUrlConfigured(): Boolean {
        val baseUrl = BuildConfig.IPTV_BASE_URL.trim()
        return baseUrl.isNotBlank() && !baseUrl.contains("example.invalid")
    }

    private fun buildLoginErrorMessage(exception: Exception): String {
        val rawMessage = exception.message.orEmpty().trim()
        return when {
            rawMessage.contains("Unable to resolve host", ignoreCase = true) -> "No se puede conectar con el servidor IPTV"
            rawMessage.contains("timeout", ignoreCase = true) -> "El servidor IPTV ha tardado demasiado en responder"
            rawMessage.contains("HTTP 401", ignoreCase = true) || rawMessage.contains("HTTP 403", ignoreCase = true) -> "Usuario o contrasena incorrectos"
            rawMessage.contains("HTTP 404", ignoreCase = true) -> "La ruta de login no existe en el servidor configurado"
            rawMessage.isNotBlank() -> rawMessage
            else -> "No se pudo iniciar sesion"
        }
    }

    private fun maskUsername(username: String): String {
        if (username.length <= 2) return "**"
        return username.take(2) + "***"
    }

    private fun eventSortScore(item: CatalogItem): Long {
        val now = Calendar.getInstance()
        val eventTime = runCatching { TIME_FORMATTER.parse(item.badgeText.ifBlank { "23:59" }) }.getOrNull() ?: return Long.MAX_VALUE
        val eventCalendar = Calendar.getInstance().apply {
            val parsed = Calendar.getInstance().apply { time = eventTime }
            set(Calendar.HOUR_OF_DAY, parsed.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, parsed.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var delta = (eventCalendar.timeInMillis - now.timeInMillis) / 60_000L
        if (delta < 0) delta += 24 * 60
        return delta
    }

    private fun normalizeImageUrl(url: String): String {
        if (url.isBlank() || url == "null") return ""
        return url
            .replace("http://${BuildConfig.IPTV_BASE_URL.removePrefix("https://").removePrefix("http://")}", BuildConfig.IPTV_BASE_URL)
            .replace("http://image.tmdb.org", "https://image.tmdb.org")
    }

    private suspend fun getJsonObject(url: String, token: String? = null): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream ?: connection.inputStream
            val body = BufferedReader(InputStreamReader(stream)).use(BufferedReader::readText)
            if (statusCode !in 200..299) throw IllegalStateException("HTTP $statusCode: $body")
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun postForm(url: String, body: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.outputStream.use { it.write(body.toByteArray()) }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream ?: connection.inputStream
            val response = BufferedReader(InputStreamReader(stream)).use(BufferedReader::readText)
            if (statusCode !in 200..299) throw IllegalStateException("HTTP $statusCode: $response")
            return JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildFormBody(vararg values: Pair<String, String>): String {
        return values.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, Charsets.UTF_8.name())}=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
    }

    private suspend fun safeSectionLoad(name: String, block: suspend () -> List<BrowseSection>): List<BrowseSection> {
        return runCatching { block() }
            .onFailure { Log.e(TAG, "Fallo cargando seccion $name", it) }
            .getOrDefault(emptyList())
    }

    private suspend fun safePlaylistLoad(forceRefresh: Boolean, useSummaryPlaylist: Boolean): M3uCatalogSnapshot {
        return runCatching {
            if (useSummaryPlaylist) {
                m3uCatalogStore.getHomeSnapshot(forceRefresh)
            } else {
                m3uCatalogStore.getCatalog(forceRefresh)
            }
        }
            .onFailure { Log.e(TAG, "Fallo cargando playlist M3U completa", it) }
            .getOrDefault(M3uCatalogSnapshot(emptyList(), emptyList(), emptyList()))
    }

    private data class RawCalendarEvent(
        val id: String,
        val title: String,
        val competition: String,
        val category: String,
        val time: String,
        val channels: List<ChannelRef>,
    )

    private data class ChannelRef(
        val channelId: String,
        val displayName: String,
        val quality: String,
        val logoUrl: String,
    )

    companion object {
        private const val TAG = "IptvRepository"
        private const val USER_AGENT = "WalacTV AndroidTV"
        private const val FEATURED_CHANNELS = 40
        private const val FEATURED_MOVIES = 30
        private const val FEATURED_SERIES = 30
        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val TIME_FORMATTER = SimpleDateFormat("HH:mm", Locale.getDefault())

        @Volatile
        private var memoryHomeCatalog: HomeCatalog? = null

        @Volatile
        private var memoryHomeSummaryCatalog: HomeCatalog? = null

        private data class StoredCredentials(
            val username: String,
            val password: String,
        )
    }
}
