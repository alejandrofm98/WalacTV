@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.example.walactv.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.walactv.R
import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.model.StreamOption
import com.example.walactv.data.model.bestTorrentFirst
import com.example.walactv.data.model.filterByPreferredLanguage
import com.example.walactv.data.model.sortedByPreferredLanguage
import com.example.walactv.data.preferences.PreferencesManager
import com.example.walactv.data.model.preferredVodPosterUrl
import com.example.walactv.data.model.playbackContentId
import com.example.walactv.data.model.toUnifiedOptions
import com.example.walactv.data.remote.api.dto.PlaybackPreferenceDto
import com.example.walactv.data.remote.repository.IptvRepository
import com.example.walactv.data.remote.torrent.TorrentioClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.layout
import com.example.walactv.data.util.isSeasonPackTitle
import com.example.walactv.data.util.languageBadgeLabel
import com.example.walactv.ui.compose.tvClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import com.bumptech.glide.Glide
import com.example.walactv.ui.theme.*

class MovieDetailFragment : Fragment() {

    private val cachedItems = mutableMapOf<String, CatalogItem>()
    private var torrentStreams by mutableStateOf<List<StreamOption>>(emptyList())
    private var torrentPrefLang by mutableStateOf<String?>(null)
    private var torrentLoading by mutableStateOf(false)
    private var torrentError by mutableStateOf(false)

    companion object {
        private const val ARG_CATALOG_ITEM = "catalog_item"
        private const val TAG = "MovieDetailFragment"

        fun newInstance(item: CatalogItem): MovieDetailFragment {
            Log.d(TAG, "TMDB_DETAIL newInstance item=${item.tmdbDebug()} streamOptions=${item.streamOptions.size}")
            return MovieDetailFragment().apply {
                cachedItems[item.stableId] = item
                arguments = createItemBundle(item)
            }
        }

        private fun createItemBundle(item: CatalogItem): Bundle {
            Log.d(TAG, "TMDB_DETAIL bundle item=${item.tmdbDebug()}")
            return Bundle().apply {
                putString("stableId", item.stableId)
                putString("imdbId", item.imdbId)
                putString("catalogId", item.catalogId)
                putString("providerId", item.providerId)
                putString("title", item.title)
                putString("description", item.description)
                putString("imageUrl", item.imageUrl)
                putString("backdropUrl", item.backdropUrl)
                putString("tmdbPosterUrl", item.tmdbPosterUrl.orEmpty())
                putDouble("voteAverage", item.voteAverage ?: 0.0)
                putInt("voteCount", item.voteCount ?: 0)
                putString("tagline", item.tagline)
                putString("releaseDate", item.releaseDate)
                putInt("runtimeMinutes", item.runtimeMinutes ?: 0)
                putStringArrayList("genres", ArrayList(item.genres))
                putString("group", item.group)
                putString("subtitle", item.subtitle)
                putString("providerId", item.providerId)
                putStringArrayList("countries", ArrayList(item.countries))
                putInt("year", item.year ?: 0)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val item = parseArguments(requireArguments())
        val repository = IptvRepository(requireContext())

        // Cargar fuentes Torrentio (consulta directa al addon) para la pelicula.
        // Solo si hay imdb_id valido; sin el no se consulta y no se marca error.
        // El listado del catalogo puede venir sin imdb_id (bug backend en dev):
        // si falta, se resuelve con el detalle /api/content/movies/{id}.
        viewLifecycleOwner.lifecycleScope.launch {
            var imdb = item.imdbId
            if (!TorrentioClient.isImdbId(imdb)) {
                val lookupId = item.catalogId ?: item.providerId ?: item.stableId
                val full = runCatching {
                    repository.fetchContentItem(ContentKind.MOVIE, lookupId)
                }.getOrNull()
                if (full != null && TorrentioClient.isImdbId(full.imdbId)) {
                    imdb = full.imdbId
                    cachedItems[item.stableId] = full
                }
            }
            if (TorrentioClient.isImdbId(imdb)) {
                torrentLoading = true
                val fetched = runCatching {
                    repository.getTorrentioMovieStreams(imdb!!)
                }.getOrElse {
                    torrentError = true
                    emptyList()
                }
                // Preferencia de idioma de la pelicula (si existe) para
                // ordenar sus torrents; si no, el idioma global.
                val catalogId = item.catalogId ?: item.providerId ?: item.stableId.substringAfter(':')
                val prefLang = runCatching {
                    repository.getPlaybackPreference("movie", catalogId)?.audioLanguage
                }.getOrNull()
                torrentPrefLang = prefLang
                torrentStreams = fetched
                torrentLoading = false
            }
        }

        return ComposeView(requireContext()).apply {
            setContent {
                WalacTVTheme {
                    MovieDetailScreen(
                        item = item,
                        torrentStreams = torrentStreams,
                        torrentPrefLang = torrentPrefLang,
                        torrentLoading = torrentLoading,
                        torrentError = torrentError,
                        onBackClick = { requireActivity().supportFragmentManager.popBackStack() },
                        onPlayClick = { playMovie() },
                        onPlaySource = { source ->
                            playMovie(source = source)
                        },
                    )
                }
            }
        }
    }

    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    private fun playMovie(
        selectedStreamUrl: String? = null,
        resumePositionMs: Long = 0L,
        source: StreamOption? = null,
    ) {
        val stableId = requireArguments().getString("stableId")
        val item = cachedItems[stableId] ?: run {
            Log.e(TAG, "playMovie: no cached item for stableId=$stableId")
            return
        }
        lifecycleScope.launch {
            val catalogId = item.catalogId ?: item.providerId ?: item.stableId.substringAfter(':')
            val preference = runCatching {
                IptvRepository(requireContext()).getPlaybackPreference("movie", catalogId)
            }.getOrNull()
            stableId?.let {
                cachedItems[it] = IptvRepository(requireContext()).orderStreamsForPlayback(
                    item,
                    // Eleccion manual del drawer: sin sondeos previos.
                    probeHealth = source == null && selectedStreamUrl == null,
                )
            }
            playMovieWithPreference(preference, resumePositionMs, selectedStreamUrl, source)
        }
    }

    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    private fun playMovieWithPreference(
        preference: PlaybackPreferenceDto?,
        resumePositionMs: Long = 0L,
        selectedStreamUrl: String? = null,
        source: StreamOption? = null,
    ) {
        val stableId = requireArguments().getString("stableId")
        val item = cachedItems[stableId] ?: run {
            Log.e(TAG, "playMovie: no cached item for stableId=$stableId")
            return
        }
        Log.d(TAG, "playMovie item=${item.tmdbDebug()} streamOptions=${item.streamOptions.size}")

        // Fuente elegida por el selector, la seleccionada por URL, luego
        // directo del proveedor y por ultimo el torrent con mas seeds en el
        // idioma preferido (ruta automatica: el drawer muestra todos).
        val fallbackTorrent = torrentStreams
            .filterByPreferredLanguage(torrentPrefLang ?: PreferencesManager.getPreferredLanguageOrDefault())
            .bestTorrentFirst()
            .firstOrNull()
        val stream = source
            ?: selectedStreamUrl?.let { url -> item.streamOptions.firstOrNull { it.url == url } }
            ?: item.streamOptions.firstOrNull { !it.isTorrent && it.url.isNotBlank() }
            ?: fallbackTorrent
            ?: item.streamOptions.firstOrNull { it.isTorrent }
        if (stream == null) {
            android.widget.Toast.makeText(requireContext(), R.string.no_streams_available, android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Si la fuente elegida es un torrent, construir el item con el magnet en
        // primer lugar y el resto de torrents como opciones de respaldo.
        val playableItem = if (stream.isTorrent) {
            val otherTorrents = torrentStreams.bestTorrentFirst()
                .filter { it.infoHash != stream.infoHash }
            val allStreams = listOf(stream) +
                item.streamOptions.filter { !it.isTorrent } +
                otherTorrents
            item.copy(streamOptions = allStreams)
        } else {
            item
        }
        val playableStream = if (stream.isTorrent) {
            StreamOption(
                label = stream.label,
                url = "magnet:?xt=urn:btih:${stream.infoHash}",
                infoHash = stream.infoHash,
                fileIdx = stream.fileIdx,
                seeders = stream.seeders,
                sizeBytes = stream.sizeBytes,
                torrentTitle = stream.torrentTitle,
                language = stream.language,
                languages = stream.languages,
                quality = stream.quality,
            )
        } else {
            stream
        }

        val unifiedOptions = playableItem.streamOptions.toUnifiedOptions()
        val playerFragment = PlayerFragment()
        playerFragment.initialize(
            streamUrl = playableStream.url,
            overlayNumber = item.kind.name,
            overlayTitle = item.title,
            overlayMeta = item.subtitle,
            overlayDescription = item.description,
            overlayRating = item.voteAverage,
            contentKind = item.kind,
            onNavigateChannel = { _ -> },
            onNavigateOption = { _ -> },
            onDirectChannelNumber = { _ -> false },
            onToggleFavorite = { false },
            onOpenFavorites = { false },
            onOpenRecents = { false },
            onOpenGuide = null,
            onNextEpisode = null,
            onPreviousEpisode = null,
            allSeriesEpisodes = emptyList(),
            currentEpisode = null,
            streamOptionLabels = playableItem.streamOptions.map { it.label },
            currentOptionIndex = 0,
            showOptionsOnStart = false,
            overlayLogoUrl = item.preferredVodPosterUrl(),
            overlayBackdropUrl = item.backdropUrl.orEmpty(),
            isFavorite = false,
            contentId = item.playbackContentId(),
            positionMs = resumePositionMs,
            onPlayerClosed = {
                view?.requestFocus()
            },
            onProgressSaved = ComposeMainFragment.progressSavedCallback,
            customHeaders = stream.headers,
            unifiedStreamOptions = unifiedOptions,
            onSelectUnifiedOption = { selectedIndex, resumeMs ->
                val selected = unifiedOptions.getOrNull(selectedIndex) ?: return@initialize
                playMovie(selected.url, resumeMs)
            },
            playbackCatalogId = item.catalogId ?: item.providerId ?: item.stableId.substringAfter(':'),
            playbackPreference = preference,
            torrentFileIdx = playableStream.fileIdx,
        )
        val fm = requireActivity().supportFragmentManager
        fm.findFragmentById(R.id.player_container)?.let { fm.beginTransaction().remove(it).commitNow() }
        fm.beginTransaction().replace(R.id.player_container, playerFragment, "player_fragment").commitNow()
        val container = requireActivity().findViewById<FrameLayout>(R.id.player_container)
        container.visibility = View.VISIBLE
        container.isFocusable = true
        container.isFocusableInTouchMode = true
        runCatching { container.requestFocus() }
    }

    private fun parseArguments(args: Bundle): CatalogItem {
        return CatalogItem(
            stableId = args.getString("stableId") ?: "",
            catalogId = args.getString("catalogId"),
            providerId = args.getString("providerId"),
            imdbId = args.getString("imdbId"),
            title = args.getString("title") ?: "",
            subtitle = "",
            description = args.getString("description") ?: "",
            imageUrl = args.getString("imageUrl") ?: "",
            kind = ContentKind.MOVIE,
            group = args.getString("group") ?: "",
            badgeText = "",
            backdropUrl = args.getString("backdropUrl"),
            voteAverage = args.getDouble("voteAverage").takeIf { it > 0 },
            voteCount = args.getInt("voteCount").takeIf { it > 0 },
            tagline = args.getString("tagline"),
            releaseDate = args.getString("releaseDate"),
            runtimeMinutes = args.getInt("runtimeMinutes").takeIf { it > 0 },
            genres = args.getStringArrayList("genres")?.toList() ?: emptyList(),
            tmdbPosterUrl = args.getString("tmdbPosterUrl") ?: "",
            countries = args.getStringArrayList("countries")?.toList() ?: emptyList(),
            year = args.getInt("year").takeIf { it > 0 },
        ).also { item ->
            Log.d(
                TAG,
                "TMDB_DETAIL parsed id=${item.stableId} title=${item.title.take(120)} " +
                    "desc=${item.description.take(160)} image=${item.imageUrl.take(160)} backdrop=${item.backdropUrl.orEmpty().take(160)} " +
                    "rating=${item.voteAverage} runtime=${item.runtimeMinutes} genres=${item.genres.joinToString("|").take(120)}",
            )
        }
    }
}

@Composable
fun MovieDetailScreen(
    item: CatalogItem,
    torrentStreams: List<StreamOption> = emptyList(),
    torrentPrefLang: String? = null,
    torrentLoading: Boolean = false,
    torrentError: Boolean = false,
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit,
    onPlaySource: (StreamOption) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val fuentesFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var showSourcePicker by remember { mutableStateOf(false) }
    var sourceSelectedIndex by remember { mutableIntStateOf(0) }

    // Torrents ordenados: primero el idioma preferido de la pelicula (o el
    // global), luego el resto. Los directos IPTV siempre van delante.
    val orderedTorrents = remember(torrentStreams, torrentPrefLang) {
        torrentStreams.sortedByPreferredLanguage(
            torrentPrefLang ?: PreferencesManager.getPreferredLanguageOrDefault(),
        )
    }

    val allSources = remember(item.streamOptions, orderedTorrents) {
        item.streamOptions.filter { !it.isTorrent && it.url.isNotBlank() } + orderedTorrents
    }

    val backgroundImageUrl = item.backdropUrl?.takeIf { it.isNotBlank() }
        ?: item.tmdbPosterUrl?.takeIf { it.isNotBlank() }
        ?: item.imageUrl.takeIf { it.isNotBlank() }

    LaunchedEffect(item.stableId) {
        Log.d(
            "MovieDetailFragment",
            "TMDB_DETAIL compose id=${item.stableId} title='${item.title}' " +
                "hasBackdrop=${!item.backdropUrl.isNullOrBlank()} hasImage=${item.imageUrl.isNotBlank()} " +
                "hasPoster=${!item.tmdbPosterUrl.isNullOrBlank()} bgUsed=${backgroundImageUrl?.take(80)} " +
                "desc=${item.description.take(80)} rating=${item.voteAverage} genres=${item.genres} " +
                "countries=${item.countries} runtime=${item.runtimeMinutes} release=${item.releaseDate}",
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IptvBackground)
    ) {
        if (!backgroundImageUrl.isNullOrBlank()) {
            AsyncImage(
                url = backgroundImageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Gradiente horizontal (oscuro izquierda -> transparente derecha)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.95f),
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = Float.POSITIVE_INFINITY
                    )
                )
        )

        // Gradiente vertical (oscuro abajo -> transparente arriba)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.8f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 56.dp, end = 24.dp, top = 48.dp, bottom = 48.dp)
        ) {
            // Botón superior izquierdo "Volver"
            Row(
                modifier = Modifier
                    .tvClickable { onBackClick() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Volver",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(0.55f),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Título
                Text(
                    text = item.title,
                    color = IptvTextPrimary,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 60.sp
                )

                // Botones
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.focusRequester(focusRequester)
                ) {
                    ActionButton(
                        text = "Reproducir",
                        icon = Icons.Default.PlayArrow,
                        isPrimary = true,
                        onClick = onPlayClick
                    )
                    ActionButton(
                        text = "Fuentes",
                        icon = Icons.Default.List,
                        isPrimary = false,
                        onClick = { showSourcePicker = true },
                        modifier = Modifier.focusRequester(fuentesFocusRequester)
                    )
                }

                // Descripción
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        color = IptvTextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Bloque de metadatos (Géneros, Año, Duración, Rating)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Línea 1: Géneros • Año
                    val line1Parts = buildList {
                        if (item.genres.isNotEmpty()) add(item.genres.joinToString(" • "))
                        item.releaseDate?.takeIf { it.isNotBlank() }?.let { add(it) }
                            ?: item.year?.toString()?.let { add(it) }
                    }
                    if (line1Parts.isNotEmpty()) {
                        Text(
                            text = line1Parts.joinToString("  •  "),
                            color = IptvTextMuted,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Línea 2: Duración • País • Rating
                    val countryNames = mapOf(
                        "AD" to "Andorra", "AE" to "Emiratos Árabes Unidos", "AF" to "Afganistán",
                        "AL" to "Albania", "AM" to "Armenia", "AR" to "Argentina", "AT" to "Austria",
                        "AU" to "Australia", "AZ" to "Azerbaiyán", "BE" to "Bélgica", "BG" to "Bulgaria",
                        "BH" to "Baréin", "BR" to "Brasil", "BY" to "Bielorrusia", "CA" to "Canadá",
                        "CH" to "Suiza", "CY" to "Chipre", "CZ" to "República Checa", "DE" to "Alemania",
                        "DK" to "Dinamarca", "DO" to "República Dominicana", "DZ" to "Argelia",
                        "EC" to "Ecuador", "EG" to "Egipto", "EN" to "Inglés", "ES" to "España",
                        "FI" to "Finlandia", "FR" to "Francia", "GB" to "Reino Unido", "GR" to "Grecia",
                        "HK" to "Hong Kong", "HN" to "Honduras", "HR" to "Croacia", "HU" to "Hungría",
                        "ID" to "Indonesia", "IE" to "Irlanda", "IL" to "Israel", "IN" to "India",
                        "IQ" to "Irak", "IR" to "Irán", "IS" to "Islandia", "IT" to "Italia",
                        "JM" to "Jamaica", "JO" to "Jordania", "JP" to "Japón", "KE" to "Kenia",
                        "KR" to "Corea del Sur", "KW" to "Kuwait", "KZ" to "Kazajistán",
                        "LB" to "Líbano", "LT" to "Lituania", "LU" to "Luxemburgo", "LV" to "Letonia",
                        "MA" to "Marruecos", "MX" to "México", "MY" to "Malasia", "NG" to "Nigeria",
                        "NL" to "Países Bajos", "NO" to "Noruega", "NP" to "Nepal", "NZ" to "Nueva Zelanda",
                        "PE" to "Perú", "PH" to "Filipinas", "PK" to "Pakistán", "PL" to "Polonia",
                        "PT" to "Portugal", "RO" to "Rumania", "RS" to "Serbia", "RU" to "Rusia",
                        "SA" to "Arabia Saudita", "SE" to "Suecia", "SG" to "Singapur", "SI" to "Eslovenia",
                        "SK" to "Eslovaquia", "TH" to "Tailandia", "TN" to "Túnez", "TR" to "Turquía",
                        "TW" to "Taiwán", "UA" to "Ucrania", "UK" to "Reino Unido", "US" to "Estados Unidos",
                        "UY" to "Uruguay", "VE" to "Venezuela", "VN" to "Vietnam", "ZA" to "Sudáfrica",
                        "CO" to "Colombia", "CL" to "Chile",
                    )
                    val line2Parts = buildList {
                        item.runtimeMinutes?.let { minutes ->
                            val hours = minutes / 60
                            val mins = minutes % 60
                            add(if (hours > 0) "${hours}h ${mins}m" else "${mins}m")
                        }
                        val displayCountries = item.countries
                            .filter { it.isNotBlank() && it != "UNKNOWN" }
                            .map { code -> countryNames[code] ?: code }
                        if (displayCountries.isNotEmpty()) {
                            add(displayCountries.joinToString(" • "))
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (line2Parts.isNotEmpty()) {
                            Text(
                                text = line2Parts.joinToString("  •  "),
                                color = IptvTextMuted,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "•",
                                color = IptvTextMuted,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        
                        item.voteAverage?.let { rating ->
                            Text(
                                text = "⭐ ${String.format(java.util.Locale.US, "%.1f", rating)}",
                                color = Color(0xFF46D369), // Verde estilo Netflix
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    if (showSourcePicker) {
        MovieSourceDrawer(
            item = item,
            streams = allSources,
            loading = torrentLoading,
            error = torrentError,
            selectedIndex = sourceSelectedIndex,
            onSelect = { sourceSelectedIndex = it },
            onPlay = {
                val source = allSources.getOrNull(sourceSelectedIndex)
                if (source != null) {
                    showSourcePicker = false
                    onPlaySource(source)
                }
            },
            onDismiss = {
                showSourcePicker = false
                scope.launch {
                    delay(80.milliseconds)
                    runCatching { fuentesFocusRequester.requestFocus() }
                }
            },
        )
    }
}

@Composable
private fun MovieSourceDrawer(
    item: CatalogItem,
    streams: List<StreamOption>,
    loading: Boolean,
    error: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    var focusedIndex by remember { mutableIntStateOf(selectedIndex) }
    val rowRequesters = remember(streams.size) { List(streams.size) { FocusRequester() } }
    val emptyCloseRequester = remember { FocusRequester() }

    // Pestañas de idioma (estilo premium): Todos / idiomas presentes en orden
    // de aparicion (el sort ya pone el preferido primero).
    var activeTab by remember { mutableIntStateOf(0) }
    val tabLabels = remember(streams) {
        buildList {
            add("Todos · ${streams.size}")
            streams.map { it.language }
                .filterNotNull()
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { add(languageBadgeLabel(it)) }
        }
    }
    // Indices globales visibles segun la pestaña activa.
    val visibleIndices = remember(streams, activeTab) {
        if (activeTab == 0) {
            streams.indices.toList()
        } else {
            val wanted = tabLabels.getOrNull(activeTab)
            streams.indices.filter { idx ->
                streams[idx].language?.let { languageBadgeLabel(it) } == wanted
            }
        }
    }

    fun moveFocusTo(index: Int) {
        if (visibleIndices.isEmpty()) return
        val clamped = index.coerceIn(visibleIndices.indices)
        val global = visibleIndices[clamped]
        focusedIndex = global
        onSelect(global)
        runCatching { rowRequesters[global].requestFocus() }
    }

    LaunchedEffect(streams, selectedIndex, loading, activeTab) {
        if (activeTab == 0) focusedIndex = selectedIndex
        delay(80.milliseconds)
        runCatching {
            when {
                visibleIndices.isNotEmpty() -> {
                    val pos = if (activeTab == 0) {
                        visibleIndices.indexOf(focusedIndex).coerceAtLeast(0)
                    } else 0
                    rowRequesters[visibleIndices[pos.coerceIn(visibleIndices.indices)]].requestFocus()
                }
                !loading -> emptyCloseRequester.requestFocus()
            }
        }
    }

    val drawerShape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)

    // Ventana propia: al abrirse, el sistema mete el foco dentro del drawer
    // y no se queda en el detalle de fondo.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
    // Sin oscurecer el fondo: el detalle sigue visible a la izquierda.
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { dialogWindow?.setDimAmount(0f) }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        ) {
            Column(
                modifier = Modifier
                    .width(500.dp)
                    .fillMaxHeight()
                    .shadow(40.dp, drawerShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0E1730), Color(0xFF0A1224)),
                        ),
                        drawerShape,
                    )
                    .border(1.dp, Color(0xFF2E2E4E), drawerShape)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionUp -> { moveFocusTo(visibleIndices.indexOf(focusedIndex) - 1); true }
                            Key.DirectionDown -> { moveFocusTo(visibleIndices.indexOf(focusedIndex) + 1); true }
                            Key.DirectionCenter, Key.Enter -> {
                                if (visibleIndices.isNotEmpty()) { onSelect(focusedIndex); onPlay() }
                                true
                            }
                            // Izquierda sobre la primera fuente cambia de pestaña
                            // de idioma; si ya es la primera, cierra.
                            Key.DirectionLeft -> {
                                val pos = visibleIndices.indexOf(focusedIndex)
                                if (pos <= 0) onDismiss() else moveFocusTo(pos - 1)
                                true
                            }
                            Key.DirectionRight -> true
                            Key.Back, Key.Escape -> { onDismiss(); true }
                            else -> false
                        }
                    }
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Fuentes de reproducción",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    item.title,
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Pestañas de idioma (Todos / Español / Inglés…). Visual only:
                // el foco sigue en las filas; ← en la primera fuente cambia.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabLabels.forEachIndexed { idx, label ->
                        val active = idx == activeTab
                        Text(
                            text = label,
                            color = if (active) Color(0xFF0B1022) else Color(0xFFB8C2D8),
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (active) Color.White else Color.White.copy(alpha = 0.07f),
                                    RoundedCornerShape(18.dp),
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))

                // La lista ocupa todo el alto disponible con scroll: el foco
                // arrastra el scroll al moverse entre filas. La barra lateral
                // indica cuanta lista queda por ver.
                val listState = rememberScrollState()
                Row(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(listState),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                    when {
                    loading -> {
                        Text("Buscando fuentes en Torrentio...", color = Color.LightGray, fontSize = 14.sp)
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                            )
                        }
                    }
                    streams.isEmpty() -> {
                        Text(
                            if (error) "No se pudieron cargar las fuentes" else "Sin fuentes disponibles",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                        )
                        var closeFocused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (closeFocused) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp),
                                )
                                .onFocusChanged { closeFocused = it.isFocused }
                                .focusRequester(emptyCloseRequester)
                                .focusable()
                                .tvClickable { onDismiss() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Cerrar",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    visibleIndices.isEmpty() -> {
                        Text(
                            "Sin fuentes en este idioma",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                        )
                    }
                    else -> {
                        visibleIndices.forEach { globalIdx ->
                            val stream = streams[globalIdx]
                            MovieSourceRow(
                                label = (stream.torrentTitle ?: stream.label).lineSequence().firstOrNull().orEmpty()
                                    .ifBlank { stream.label },
                                quality = stream.quality,
                                language = stream.language,
                                languages = stream.languages,
                                seeders = stream.seeders,
                                size = stream.sizeBytes,
                                isTorrent = stream.isTorrent,
                                isPack = stream.isTorrent && isSeasonPackTitle(stream.torrentTitle),
                                isSelected = globalIdx == focusedIndex,
                                focusRequester = rowRequesters[globalIdx],
                                onFocused = { focusedIndex = globalIdx; onSelect(globalIdx) },
                                onConfirm = { focusedIndex = globalIdx; onSelect(globalIdx); onPlay() },
                            )
                        }
                    }
                    }
                    // Barra de scroll indicadora: su posicion muestra cuanto
                    // queda de la lista; se mueve con el foco automaticamente.
                    if (listState.maxValue > 0) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .padding(start = 4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(2.dp)),
                            )
                            val scrollFraction = listState.value.toFloat() / listState.maxValue
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.25f)
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        val maxOffset = placeable.height * 3
                                        val offsetY = (scrollFraction * maxOffset).toInt()
                                        layout(placeable.width, placeable.height) {
                                            placeable.placeRelative(0, offsetY)
                                        }
                                    }
                                    .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun MovieSourceRow(
    label: String,
    quality: String?,
    language: String?,
    languages: List<String> = emptyList(),
    seeders: Int?,
    size: Long?,
    isTorrent: Boolean,
    isSelected: Boolean,
    isPack: Boolean = false,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onConfirm: () -> Unit,
) {
    val border = if (isSelected) IptvAccent else Color(0xFFB8C2D8).copy(alpha = 0.14f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) IptvAccent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.025f))
            .border(2.dp, border, RoundedCornerShape(14.dp))
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { event ->
                // OK en la fila reproduce directo, como en Stremio. El clickable
                // queda solo para puntero; las teclas las posee este handler.
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onConfirm()
                    true
                } else false
            }
            .clickable { onConfirm() }
            .padding(horizontal = 15.dp, vertical = 12.dp),
    ) {
        Column {
            // Chips superiores: calidad + PACK + idiomas (todos los del release).
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                quality?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it.uppercase(),
                        color = if (isSelected) Color.White else Color(0xFFB8C2D8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                if (isSelected) IptvAccent else Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                if (isPack) {
                    Text(
                        "PACK",
                        color = Color(0xFFD9A8FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFFC77DFF).copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                val langChips = languages.ifEmpty { listOfNotNull(language) }
                langChips.filter { it.isNotBlank() }.distinct().forEach { lang ->
                    Text(
                        languageBadgeLabel(lang),
                        color = Color(0xFF9DB7FF),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(Color(0xFF9DB7FF).copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                text = label,
                color = if (isSelected) Color.White else Color(0xFFB8C2D8),
                fontSize = 14.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 7.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 7.dp),
            ) {
                seeders?.let {
                    Text("$it seeds", color = Color(0xFF46D369), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
                size?.let {
                    val gb = it / (1024.0 * 1024.0 * 1024.0)
                    Text(
                        if (gb >= 1) String.format(java.util.Locale.US, "%.1f GB", gb) else "${it / (1024 * 1024)} MB",
                        color = Color(0xFF8692AA),
                        fontSize = 12.5.sp,
                    )
                }
                Text(
                    if (isTorrent) "Torrentio" else "IPTV directo",
                    color = Color(0xFF8692AA),
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun AsyncImage(url: String, contentDescription: String?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        modifier = modifier,
        update = { imageView ->
            Glide.with(imageView)
                .load(url)
                .into(imageView)
        }
    )
}

@Composable
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f)

    val bgColor = if (isPrimary) Color.White else Color.Transparent
    val contentColor = if (isPrimary) Color.Black else Color.White
    val borderModifier = if (!isPrimary) Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)) else Modifier

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .scale(scale)
            .then(borderModifier)
            .background(bgColor, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .tvClickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            color = contentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
