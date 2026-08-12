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
import com.example.walactv.data.model.preferredVodPosterUrl
import com.example.walactv.data.model.playbackContentId
import com.example.walactv.data.model.toUnifiedOptions
import com.example.walactv.data.remote.api.dto.PlaybackPreferenceDto
import com.example.walactv.data.remote.repository.IptvRepository
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.example.walactv.ui.compose.tvClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.bumptech.glide.Glide
import com.example.walactv.ui.theme.*

class MovieDetailFragment : Fragment() {

    private val cachedItems = mutableMapOf<String, CatalogItem>()

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

        return ComposeView(requireContext()).apply {
            setContent {
                WalacTVTheme {
                    MovieDetailScreen(
                        item = item,
                        onBackClick = { requireActivity().supportFragmentManager.popBackStack() },
                        onPlayClick = { playMovie() }
                    )
                }
            }
        }
    }

    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    private fun playMovie(
        selectedStreamUrl: String? = null,
        resumePositionMs: Long = 0L,
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
            playMovieWithPreference(preference, resumePositionMs, selectedStreamUrl)
        }
    }

    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    private fun playMovieWithPreference(
        preference: PlaybackPreferenceDto?,
        resumePositionMs: Long = 0L,
        selectedStreamUrl: String? = null,
    ) {
        val stableId = requireArguments().getString("stableId")
        val item = cachedItems[stableId] ?: run {
            Log.e(TAG, "playMovie: no cached item for stableId=$stableId")
            return
        }
        Log.d(TAG, "playMovie item=${item.tmdbDebug()} streamOptions=${item.streamOptions.size}")

        val stream = selectedStreamUrl?.let { url ->
            item.streamOptions.firstOrNull { it.url == url }
        } ?: item.streamOptions.firstOrNull()
        if (stream == null) {
            android.widget.Toast.makeText(requireContext(), R.string.no_streams_available, android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val unifiedOptions = item.streamOptions.toUnifiedOptions()
        val playerFragment = PlayerFragment()
        playerFragment.initialize(
            streamUrl = stream.url,
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
            streamOptionLabels = item.streamOptions.map { it.label },
            currentOptionIndex = 0,
            showOptionsOnStart = false,
            overlayLogoUrl = item.preferredVodPosterUrl(),
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
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

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
