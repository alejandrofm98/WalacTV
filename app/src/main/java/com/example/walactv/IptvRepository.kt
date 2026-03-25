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

    // ── Caches ────────────────────────────────────────────────────────────────

    /** Clave: "<KIND>|<country>" — country vacío = sin filtro de país */
    private val filterCache = mutableMapOf<String, CatalogFilters>()

    @Volatile private var memoryHomeCatalog: HomeCatalog? = null

    // ── Credenciales / sesión ─────────────────────────────────────────────────

    fun setPlaylistProgressListener(listener: ((PlaylistLoadProgress) -> Unit)?) {
        m3uCatalogStore.progressListener = listener
    }

    fun hasStoredCredentials(): Boolean = credentialStore.hasCredentials()
    fun currentUsername(): String = credentialStore.username()

    suspend fun signIn(username: String, password: String) {
        val user = username.trim()
        val pass = password.trim()
        require(user.isNotBlank() && pass.isNotBlank()) { "Introduce usuario y contraseña" }
        require(isBaseUrlConfigured()) { "Falta configurar walactv.iptvBaseUrl en local.properties" }

        Log.d(TAG, "Intentando login en ${BuildConfig.IPTV_BASE_URL} con usuario ${maskUsername(user)}")
        try {
            val response = postForm(
                url = "${BuildConfig.IPTV_BASE_URL}/api/auth/login",
                body = buildFormBody("username" to user, "password" to pass),
            )
            accessToken = response.getString("access_token")
            credentialStore.save(user, pass)
            clearAllCaches()
            m3uCatalogStore.clearAllCache()
            Log.d(TAG, "Login correcto para ${maskUsername(user)}")
        } catch (e: Exception) {
            Log.e(TAG, "Fallo en login para ${maskUsername(user)}", e)
            throw IllegalStateException(buildLoginErrorMessage(e), e)
        }
    }

    fun signOut() {
        accessToken = null
        credentialStore.clear()
        clearAllCaches()
        m3uCatalogStore.clearAllCache()
    }

    fun clearHomeMemoryCache() = clearAllCaches()

    suspend fun refreshPlaylistNow(): Long {
        m3uCatalogStore.refreshNow()
        memoryHomeCatalog = null
        return m3uCatalogStore.getLastUpdatedMillis()
    }

    fun getLastPlaylistUpdateMillis(): Long = m3uCatalogStore.getLastUpdatedMillis()

    fun shouldRefreshPlaylistInBackground(): Boolean = m3uCatalogStore.needsBackgroundRefresh()

    fun hasPlaylistCache(): Boolean = m3uCatalogStore.hasCache()

    fun getPlaylistCacheSizeBytes(): Long = m3uCatalogStore.getCacheSizeBytes()

    suspend fun refreshPlaylistInBackground() {
        m3uCatalogStore.refreshNow()
        memoryHomeCatalog = null
    }

    private fun clearAllCaches() {
        memoryHomeCatalog = null
        filterCache.clear()
    }

    // ── Home catalog ──────────────────────────────────────────────────────────

    suspend fun loadHomeCatalog(forceRefresh: Boolean = false): HomeCatalog =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) memoryHomeCatalog?.let { return@withContext it }

            coroutineScope {
                val eventsDeferred = async {
                    safeSectionLoad("eventos") { fetchEventSections(getAccessToken()) }
                }
                val remoteDeferred = async {
                    runCatching { fetchRemoteHomeCatalog(getAccessToken()) }
                        .onFailure { Log.e(TAG, "Fallo cargando home remota", it) }
                        .getOrDefault(HomeCatalog(emptyList(), emptyList()))
                }

                val eventSections = eventsDeferred.await()
                    .map { s -> s.copy(items = resolveStreamTemplates(s.items)) }
                val remote = remoteDeferred.await()

                HomeCatalog(
                    sections = eventSections + remote.sections,
                    searchableItems = (eventSections.flatMap(BrowseSection::items) + remote.searchableItems)
                        .distinctBy(CatalogItem::stableId),
                ).also { memoryHomeCatalog = it }
            }
        }

    suspend fun loadEventsOnly(): HomeCatalog = withContext(Dispatchers.IO) {
        val sections = safeSectionLoad("eventos") { fetchEventSections(getAccessToken()) }
        val resolved = sections.map { s -> s.copy(items = resolveStreamTemplates(s.items)) }
        HomeCatalog(
            sections = resolved,
            searchableItems = resolved.flatMap(BrowseSection::items).distinctBy(CatalogItem::stableId),
        )
    }

    // ── Filtros ───────────────────────────────────────────────────────────────

    /**
     * Carga los filtros disponibles para un tipo de contenido.
     *
     * - Primera llamada (sin [country]): obtiene países Y todos los grupos.
     * - Llamada tras seleccionar país ([country] != null): refresca solo los grupos
     *   para ese país y devuelve los mismos países ya cacheados.
     *
     * Los resultados se cachean con clave "<KIND>|<country>".
     */
    suspend fun loadCatalogFilters(
        kind: ContentKind,
        country: String? = null,
    ): CatalogFilters = withContext(Dispatchers.IO) {
        if (kind == ContentKind.EVENT) return@withContext CatalogFilters()

        val cacheKey = "${kind.name}|${country.orEmpty()}"
        filterCache[cacheKey]?.let { return@withContext it }

        val contentType = kind.toApiType()
        val token = getAccessToken()

        val countries: List<CatalogFilterOption>
        val countriesForGroupQuery: String

        if (country == null) {
            // Primera carga: traemos países y construimos la query con todos ellos
            val payload = getJsonObject(
                url = "${BuildConfig.IPTV_BASE_URL}/api/content/countries?content_type=$contentType",
                token = token,
            )
            countries = parseRemoteFilterOptions(payload, "countries")
            countriesForGroupQuery = countries.joinToString(",", transform = CatalogFilterOption::value)
        } else {
            // El usuario seleccionó un país: reutilizamos los países ya cacheados
            val baseKey = "${kind.name}|"
            countries = filterCache[baseKey]?.countries ?: run {
                val payload = getJsonObject(
                    url = "${BuildConfig.IPTV_BASE_URL}/api/content/countries?content_type=$contentType",
                    token = token,
                )
                parseRemoteFilterOptions(payload, "countries")
            }
            countriesForGroupQuery = country
        }

        val groupsPayload = getJsonObject(
            url = buildGroupsUrl(contentType, countriesForGroupQuery),
            token = token,
        )
        val groups = parseRemoteFilterOptions(groupsPayload, "groups")

        CatalogFilters(countries = countries, groups = groups)
            .also { filterCache[cacheKey] = it }
    }

    // ── Contenido paginado ────────────────────────────────────────────────────

    /**
     * Carga una página de contenido.
     *
     * Úsalo para la carga inicial (page=1) y para la paginación lazy cuando el
     * usuario llega al final de la lista (page=N).  Tamaño de página fijo: 50.
     */
    suspend fun loadCatalogPage(
        kind: ContentKind,
        page: Int,
        country: String? = null,
        group: String? = null,
        search: String? = null,
    ): RemoteCatalogPage = withContext(Dispatchers.IO) {
        if (kind == ContentKind.EVENT) {
            return@withContext RemoteCatalogPage(emptyList(), 0, page, 0, 0, false, false)
        }

        val token = getAccessToken()
        val url = buildContentUrl(kind.toApiType(), page, country, group, search)
        val payload = getJsonObject(url = url, token = token)
        val parsed = parseRemoteCatalogPage(payload, expectedKind = kind)
        parsed.copy(items = resolveStreamTemplates(parsed.items).distinctBy(CatalogItem::stableId))
    }

    // ── Búsqueda ──────────────────────────────────────────────────────────────

    suspend fun searchCatalog(query: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val token = getAccessToken()
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val pass = credentialStore.password()
        val passParam = if (pass.isNotBlank()) "&password=${URLEncoder.encode(pass, UTF8)}" else ""
        val payload = getJsonObject(
            url = "${BuildConfig.IPTV_BASE_URL}/api/search?q=$encoded&page=1&page_size=60$passParam",
            token = token,
        )
        resolveStreamTemplates(parseRemoteCatalogPage(payload).items).distinctBy(CatalogItem::stableId)
    }

    // ── Series / episodios ────────────────────────────────────────────────────

    suspend fun loadSeriesEpisodes(seriesName: String): List<CatalogItem> =
        withContext(Dispatchers.IO) {
            if (seriesName.isBlank()) return@withContext emptyList()
            val token = getAccessToken()
            val encoded = encodePathSegment(seriesName)
            val pass = credentialStore.password()
            val passParam = if (pass.isNotBlank()) "&password=${URLEncoder.encode(pass, UTF8)}" else ""
            val items = mutableListOf<CatalogItem>()
            var page = 1
            do {
                val payload = getJsonObject(
                    url = "${BuildConfig.IPTV_BASE_URL}/api/series/$encoded/episodes?page=$page&page_size=100$passParam",
                    token = token,
                )
                val parsed = parseRemoteCatalogPage(payload, expectedKind = ContentKind.SERIES)
                items += parsed.items
                page++
                if (!parsed.hasNext) break
            } while (true)
            resolveStreamTemplates(items).distinctBy(CatalogItem::stableId)
        }

    // ── Eventos ───────────────────────────────────────────────────────────────

    suspend fun resolveEventItem(eventItem: CatalogItem): CatalogItem =
        withContext(Dispatchers.IO) {
            if (eventItem.kind != ContentKind.EVENT) return@withContext eventItem
            if (eventItem.streamOptions.isNotEmpty()) return@withContext eventItem
            eventItem
        }

    // ══════════════════════════════════════════════════════════════════════════
    // Implementación privada
    // ══════════════════════════════════════════════════════════════════════════

    private suspend fun fetchRemoteHomeCatalog(token: String): HomeCatalog {
        val country = PreferencesManager.getPreferredLanguageOrDefault()
        val encoded = URLEncoder.encode(country, Charsets.UTF_8.name())
        val pass = credentialStore.password()
        val passParam = if (pass.isNotBlank()) "&password=${URLEncoder.encode(pass, UTF8)}" else ""
        val payload = getJsonObject(
            url = "${BuildConfig.IPTV_BASE_URL}/api/home?country=$encoded$passParam",
            token = token,
        )
        return resolveStreamTemplates(parseRemoteHomeCatalog(payload))
    }

    private suspend fun fetchEventSections(token: String): List<BrowseSection> {
        val today = DATE_FORMATTER.format(Date())
        val pass = credentialStore.password()
        val passParam = if (pass.isNotBlank()) "?password=${URLEncoder.encode(pass, UTF8)}" else ""
        val payload = getJsonObject("${BuildConfig.IPTV_BASE_URL}/api/calendar/$today$passParam", token)
        val eventsArray = payload.optJSONArray("eventos") ?: JSONArray()
        if (eventsArray.length() == 0) return emptyList()

        val items = (0 until eventsArray.length())
            .mapNotNull { i -> parseCalendarEvent(eventsArray.getJSONObject(i)) }

        if (items.isEmpty()) return emptyList()

        return buildList {
            add(BrowseSection("Eventos de hoy", items))
            items.groupBy { it.group.ifBlank { "Agenda" } }
                .entries
                .sortedByDescending { it.value.size }
                .forEach { (key, value) -> add(BrowseSection("Eventos · $key", value)) }
        }
    }

    private fun parseCalendarEvent(obj: JSONObject): CatalogItem? {
        val resolvedChannels = obj.optJSONArray("canales_resueltos") ?: JSONArray()
        val channelRefs = (0 until resolvedChannels.length()).mapNotNull { i ->
            val ch = resolvedChannels.getJSONObject(i)
            val id = ch.optString("channel_id").ifBlank { return@mapNotNull null }
            ChannelRef(
                channelId = id,
                displayName = ch.optString("display_name"),
                quality = ch.optString("quality"),
                logoUrl = normalizeImageUrl(ch.optString("logo")),
                streamUrl = ch.optString("stream_url").takeUnless { it == "null" }.orEmpty(),
            )
        }

        val options = channelRefs.mapNotNull { ch ->
            val url = ch.streamUrl.ifBlank { buildChannelUrl(ch.channelId) }
            if (url.isBlank()) null
            else StreamOption(
                label = listOf(ch.displayName, ch.quality).filter(String::isNotBlank).joinToString(" · "),
                url = url,
            )
        }.distinctBy(StreamOption::url)

        if (options.isEmpty()) return null

        return CatalogItem(
            stableId = obj.optString("id"),
            title = obj.optString("equipos"),
            subtitle = listOf(obj.optString("hora"), obj.optString("competicion"))
                .filter(String::isNotBlank).joinToString("  •  "),
            description = channelRefs.joinToString(" · ") { it.displayName },
            imageUrl = channelRefs.firstOrNull()?.logoUrl.orEmpty(),
            kind = ContentKind.EVENT,
            group = obj.optString("categoria").ifBlank { "Agenda" },
            badgeText = obj.optString("hora"),
            streamOptions = options,
        )
    }

    // ── Resolución de streams ─────────────────────────────────────────────────

    private fun resolveStreamTemplates(catalog: HomeCatalog): HomeCatalog = HomeCatalog(
        sections = catalog.sections.map { s -> s.copy(items = resolveStreamTemplates(s.items)) },
        searchableItems = resolveStreamTemplates(catalog.searchableItems),
    )

    private fun resolveStreamTemplates(items: List<CatalogItem>): List<CatalogItem> {
        val user = credentialStore.username()
        val pass = credentialStore.password()
        return items.map { item ->
            item.copy(streamOptions = item.streamOptions.map { opt ->
                opt.copy(url = resolveStreamTemplate(opt.url, user, pass))
            })
        }
    }

    private fun buildChannelUrl(channelId: String): String {
        if (channelId.isBlank()) return ""
        val c = requireCredentials()
        return "${BuildConfig.IPTV_BASE_URL}/${c.username}/${c.password}/$channelId"
    }

    // ── Construcción de URLs ──────────────────────────────────────────────────

    private fun buildContentUrl(
        contentType: String,
        page: Int,
        country: String?,
        group: String?,
        search: String?,
    ): String = buildString {
        val pass = credentialStore.password()
        append("${BuildConfig.IPTV_BASE_URL}/api/content")
        append("?content_type=$contentType")
        append("&page=$page")
        append("&page_size=$PAGE_SIZE")
        country?.takeIf(String::isNotBlank)?.let { append("&country=${URLEncoder.encode(it, UTF8)}") }
        group?.takeIf(String::isNotBlank)?.let { append("&group=${URLEncoder.encode(it, UTF8)}") }
        search?.takeIf(String::isNotBlank)?.let { append("&search=${URLEncoder.encode(it, UTF8)}") }
        if (pass.isNotBlank()) append("&password=${URLEncoder.encode(pass, UTF8)}")
    }

    private fun buildGroupsUrl(contentType: String, countries: String): String = buildString {
        append("${BuildConfig.IPTV_BASE_URL}/api/content/groups?content_type=$contentType")
        if (countries.isNotBlank()) append("&countries=${URLEncoder.encode(countries, UTF8)}")
    }

    // ── Helpers de ContentKind ────────────────────────────────────────────────

    private fun ContentKind.toApiType(): String = when (this) {
        ContentKind.CHANNEL -> "channels"
        ContentKind.MOVIE   -> "movies"
        ContentKind.SERIES  -> "series"
        ContentKind.EVENT   -> error("Los eventos no tienen tipo API")
    }

    // ── Sesión / token ────────────────────────────────────────────────────────

    private suspend fun getAccessToken(): String {
        accessToken?.let { return it }
        val c = requireCredentials()
        val response = postForm(
            url = "${BuildConfig.IPTV_BASE_URL}/api/auth/login",
            body = buildFormBody("username" to c.username, "password" to c.password),
        )
        return response.getString("access_token").also { accessToken = it }
    }

    private fun requireCredentials(): StoredCredentials {
        val user = credentialStore.username()
        val pass = credentialStore.password()
        check(user.isNotBlank() && pass.isNotBlank()) { "No hay sesión iniciada" }
        return StoredCredentials(user, pass)
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private suspend fun getJsonObject(url: String, token: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 20_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent", USER_AGENT)
                token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }

                val status = conn.responseCode
                val body = (if (status in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream)
                    .bufferedReader().use(BufferedReader::readText)
                if (status !in 200..299) throw IllegalStateException("HTTP $status: $body")
                JSONObject(body)
            } finally {
                conn.disconnect()
            }
        }

    private suspend fun postForm(url: String, body: String): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 20_000
                conn.readTimeout = 20_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.outputStream.use { it.write(body.toByteArray()) }

                val status = conn.responseCode
                val response = (if (status in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream)
                    .bufferedReader().use(BufferedReader::readText)
                if (status !in 200..299) throw IllegalStateException("HTTP $status: $response")
                JSONObject(response)
            } finally {
                conn.disconnect()
            }
        }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private fun buildFormBody(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, UTF8)}=${URLEncoder.encode(v, UTF8)}"
        }

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, UTF8).replace("+", "%20")

    private fun normalizeImageUrl(url: String): String {
        if (url.isBlank() || url == "null") return ""
        return url
            .replace(
                "http://${BuildConfig.IPTV_BASE_URL.removePrefix("https://").removePrefix("http://")}",
                BuildConfig.IPTV_BASE_URL,
            )
            .replace("http://image.tmdb.org", "https://image.tmdb.org")
    }

    private fun isBaseUrlConfigured(): Boolean {
        val base = BuildConfig.IPTV_BASE_URL.trim()
        return base.isNotBlank() && !base.contains("example.invalid")
    }

    private fun buildLoginErrorMessage(e: Exception): String {
        val msg = e.message.orEmpty().trim()
        return when {
            msg.contains("Unable to resolve host", ignoreCase = true) ->
                "No se puede conectar con el servidor IPTV"
            msg.contains("timeout", ignoreCase = true) ->
                "El servidor IPTV ha tardado demasiado en responder"
            msg.contains("HTTP 401", ignoreCase = true) || msg.contains("HTTP 403", ignoreCase = true) ->
                "Usuario o contraseña incorrectos"
            msg.contains("HTTP 404", ignoreCase = true) ->
                "La ruta de login no existe en el servidor configurado"
            msg.isNotBlank() -> msg
            else -> "No se pudo iniciar sesión"
        }
    }

    private fun maskUsername(username: String): String =
        if (username.length <= 2) "**" else username.take(2) + "***"

    private suspend fun safeSectionLoad(
        name: String,
        block: suspend () -> List<BrowseSection>,
    ): List<BrowseSection> =
        runCatching { block() }
            .onFailure { Log.e(TAG, "Fallo cargando sección $name", it) }
            .getOrDefault(emptyList())

    // ── Modelos privados ──────────────────────────────────────────────────────

    private data class ChannelRef(
        val channelId: String,
        val displayName: String,
        val quality: String,
        val logoUrl: String,
        val streamUrl: String,
    )

    private data class StoredCredentials(val username: String, val password: String)

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "IptvRepository"
        private const val USER_AGENT = "WalacTV AndroidTV"
        private const val PAGE_SIZE = 50
        private const val UTF8 = "UTF-8"
        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
