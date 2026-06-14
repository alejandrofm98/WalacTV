@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.example.walactv

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageView.ScaleType.CENTER_CROP
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.walactv.ui.compose.buildEpisodeLabel
import com.example.walactv.ui.compose.tvClickable
import com.google.gson.Gson
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ArrowBack

import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.bumptech.glide.Glide
import com.example.walactv.preferredVodPosterUrl
import com.example.walactv.ui.theme.*

class SeriesDetailFragment : Fragment() {
    private lateinit var repository: IptvRepository

    companion object {
        private const val TAG = "SeriesDetailFragment"
        private const val PLAYER_FRAGMENT_TAG = "player_fragment"
        private const val ARG_SERIES_ITEM = "series_item"
        
        fun newInstance(item: CatalogItem) = SeriesDetailFragment().apply {
            arguments = Bundle().apply { putString(ARG_SERIES_ITEM, Gson().toJson(item)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = IptvRepository(requireContext())
        Log.d(TAG, "SeriesDetailFragment created for series")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val seriesItemJson = arguments?.getString(ARG_SERIES_ITEM)
        val catalogItem = if (seriesItemJson != null) Gson().fromJson(seriesItemJson, CatalogItem::class.java) else null
        val seriesName = catalogItem?.seriesName ?: catalogItem?.title ?: ""

        Log.d(TAG, "SeriesDetailFragment: seriesName='$seriesName'")
        return ComposeView(requireContext()).apply {
            setContent {
                WalacTVTheme {
                    SeriesDetailScreen(
                        seriesName = seriesName,
                        initialSeriesItem = catalogItem,
                        repository = repository,
                        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() }
                    ) { item, allEpisodesForSeries, logicalEpisodes ->
                        playEpisode(item, allEpisodesForSeries, logicalEpisodes)
                    }
                }
            }
        }
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun playEpisode(item: CatalogItem, allEpisodesForSeries: List<CatalogItem>, logicalEpisodes: List<CatalogItem>) {
        val preferredLanguage = PreferencesManager.getPreferredLanguageOrDefault()
        val episodeToPlay = allEpisodesForSeries.find {
            it.stableId == item.stableId ||
                    (it.seriesName == item.seriesName &&
                            it.seasonNumber == item.seasonNumber &&
                            it.episodeNumber == item.episodeNumber &&
                            normalizeLanguageCode(it.idioma) == normalizeLanguageCode(preferredLanguage))
        } ?: item

        val stream = episodeToPlay.streamOptions.firstOrNull() ?: return
        Log.d(TAG, "TMDB_SERIES_PLAY item=${item.tmdbDebug()} episode=${episodeToPlay.tmdbDebug()}")

        val currentIndex = logicalEpisodes.indexOfFirst {
            it.seriesName == episodeToPlay.seriesName &&
                    it.seasonNumber == episodeToPlay.seasonNumber &&
                    it.episodeNumber == episodeToPlay.episodeNumber
        }
        val nextEpisodeCallback: (() -> Unit)? = if (currentIndex >= 0 && currentIndex < logicalEpisodes.size - 1) {
            { playEpisode(logicalEpisodes[currentIndex + 1], allEpisodesForSeries, logicalEpisodes) }
        } else {
            null
        }
        val previousEpisodeCallback: (() -> Unit)? = if (currentIndex > 0) {
            { playEpisode(logicalEpisodes[currentIndex - 1], allEpisodesForSeries, logicalEpisodes) }
        } else {
            null
        }

        val seriesContentId = episodeToPlay.seriesKey ?: episodeToPlay.providerId ?: episodeToPlay.stableId
        Log.d(TAG, "SERIES_CONTENT_ID seriesKey=${episodeToPlay.seriesKey} providerId=${episodeToPlay.providerId} stableId=${episodeToPlay.stableId} -> $seriesContentId")

        val playerFragment = PlayerFragment()
        val unifiedOptions = episodeToPlay.streamOptions.toUnifiedOptions()
        playerFragment.initialize(
            streamUrl = stream.url,
            overlayNumber = item.kind.name,
            overlayTitle = episodeToPlay.title,
            overlayMeta = buildEpisodeLabel(episodeToPlay.seasonNumber, episodeToPlay.episodeNumber).ifBlank { episodeToPlay.subtitle },
            contentKind = item.kind,
            onNavigateChannel = { _ -> },
            onNavigateOption = { _ -> },
            onDirectChannelNumber = { _ -> false },
            onToggleFavorite = { false },
            onOpenFavorites = { false },
            onOpenRecents = { false },
            onNextEpisode = nextEpisodeCallback,
            onPreviousEpisode = previousEpisodeCallback,
            allSeriesEpisodes = allEpisodesForSeries,
            currentEpisode = episodeToPlay,
            contentId = seriesContentId,
            onPlayerClosed = {
                view?.requestFocus()
            },
            unifiedStreamOptions = unifiedOptions,
            onSelectUnifiedOption = { selectedIndex ->
                val selectedOption = unifiedOptions.getOrNull(selectedIndex) ?: return@initialize
                val freshEpisode = allEpisodesForSeries.find { ep ->
                    ep.seriesName == episodeToPlay.seriesName &&
                        ep.seasonNumber == episodeToPlay.seasonNumber &&
                        ep.episodeNumber == episodeToPlay.episodeNumber &&
                        ep.streamOptions.any { s -> s.url == selectedOption.url }
                } ?: episodeToPlay
                playEpisode(freshEpisode, allEpisodesForSeries, logicalEpisodes)
            },
        )
        val fragmentManager = requireActivity().supportFragmentManager
        fragmentManager.findFragmentById(R.id.player_container)?.let { existing ->
            fragmentManager.beginTransaction()
                .remove(existing)
                .commitNow()
        }

        fragmentManager.beginTransaction()
            .replace(R.id.player_container, playerFragment, PLAYER_FRAGMENT_TAG)
            .commitNow()

        val container = requireActivity().findViewById<FrameLayout>(R.id.player_container)
        container.visibility = View.VISIBLE
        container.isFocusable = true
        container.isFocusableInTouchMode = true
        container.requestFocus()
    }
}

@Composable
fun SeriesDetailScreen(
    seriesName: String,
    initialSeriesItem: CatalogItem?,
    repository: IptvRepository,
    onBack: () -> Unit,
    onEpisodeClick: (CatalogItem, List<CatalogItem>, List<CatalogItem>) -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var focusedEpisode by remember { mutableStateOf<CatalogItem?>(null) }

    val context = LocalContext.current
    val preferredLanguage = remember { PreferencesManager.getPreferredLanguageOrDefault() }

    val allEpisodesState = produceState<List<CatalogItem>>(initialValue = emptyList(), seriesName) {
        try {
            loadError = null
            val episodes = repository.loadSeriesEpisodes(seriesName)
            if (episodes.isEmpty()) {
                loadError = "No se encontraron episodios para '$seriesName'"
            }
            value = episodes.sortedWith(compareBy({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }))
        } catch (e: Exception) {
            loadError = "Error: ${e.message}"
            value = emptyList()
        } finally {
            isLoading = false
        }
    }
    val allEpisodes = allEpisodesState.value

    val watchProgressRepo = remember { (context.applicationContext as WalacApp).appComponent.watchProgressRepository }
    val progressMap by produceState<Map<String, WatchProgressItem>>(emptyMap(), seriesName) {
        val inProgress = watchProgressRepo.getContinueWatching().getOrDefault(emptyList())
        val watched = watchProgressRepo.getWatchedItems().getOrDefault(emptyList())
        val all = (inProgress + watched).filter {
            it.seriesName?.equals(seriesName, ignoreCase = true) == true
        }
        value = all.associateBy { it.contentId }
    }

    val uniqueEpisodes = remember(allEpisodes, preferredLanguage) {
        allEpisodes.uniqueSeriesEpisodes(preferredLanguage)
    }

    val seasons = remember(uniqueEpisodes) {
        uniqueEpisodes.mapNotNull { it.seasonNumber }.distinct().sorted()
    }

    var selectedSeason by remember { mutableIntStateOf(seasons.firstOrNull() ?: 1) }
    val episodesListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(seasons) {
        selectedSeason = seasons.firstOrNull() ?: 1
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Cargando...", color = Color.White)
        }
        return
    }

    if (loadError != null || allEpisodes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(loadError ?: "No se encontraron episodios", color = Color.White)
        }
        return
    }

    val seriesItem = initialSeriesItem ?: allEpisodes.firstOrNull { it.totalSeasons != null } ?: allEpisodes.firstOrNull()
    val bgUrl = seriesItem?.backdropUrl?.takeIf { it.isNotBlank() }
        ?: seriesItem?.tmdbPosterUrl?.takeIf { it.isNotBlank() }
        ?: seriesItem?.imageUrl?.takeIf { it.isNotBlank() }
        ?: ""
    val seriesDisplayName = seriesItem?.tmdbTitle?.takeIf { it.isNotBlank() } ?: seriesItem?.title?.takeIf { it.isNotBlank() } ?: seriesName
    val totalSeasons = seriesItem?.totalSeasons ?: seasons.size
    val year = seriesItem?.year?.toString() ?: seriesItem?.releaseDate?.take(4) ?: ""
    val genres = seriesItem?.genres?.joinToString(", ") ?: ""
    val synopsis = focusedEpisode?.description?.takeIf { it.isNotBlank() }
        ?: seriesItem?.description?.takeIf { it.isNotBlank() }
        ?: "Sin sinopsis disponible."

    Log.d("SeriesDetailScreen", "=== SERIES DETAIL DEBUG ===")
    Log.d("SeriesDetailScreen", "seriesName=$seriesName seriesDisplayName=$seriesDisplayName")
    Log.d("SeriesDetailScreen", "bgUrl='$bgUrl'")
    Log.d("SeriesDetailScreen", "backdropUrl='${seriesItem?.backdropUrl}'")
    Log.d("SeriesDetailScreen", "tmdbPosterUrl='${seriesItem?.tmdbPosterUrl}'")
    Log.d("SeriesDetailScreen", "imageUrl='${seriesItem?.imageUrl}'")
    Log.d("SeriesDetailScreen", "description='${seriesItem?.description?.take(120)}'")
    Log.d("SeriesDetailScreen", "synopsis='${synopsis.take(120)}'")
    Log.d("SeriesDetailScreen", "year=$year genres=$genres totalSeasons=$totalSeasons")

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (bgUrl.isNotBlank()) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { iv ->
                    Log.d("SeriesDetailScreen", "Glide loading bgUrl='$bgUrl' into iv=${iv.width}x${iv.height}")
                    Glide.with(iv)
                        .load(bgUrl)
                        .override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)
                        .into(iv)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Log.w("SeriesDetailScreen", "bgUrl is blank, not loading background image")
        }

        Box(modifier = Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.horizontalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.6f), Color.Transparent),
                startX = 0f, endX = 1500f
            )
        ))
        Box(modifier = Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f), Color.Black),
                startY = 400f, endY = 1080f
            )
        ))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(32.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .tvClickable { onBack() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(androidx.compose.material.icons.Icons.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Volver", color = Color.White, fontSize = 16.sp)
                }
            }

            item {
                Spacer(Modifier.height(50.dp))
            }

            item {
                Column(modifier = Modifier.fillMaxWidth(0.55f)) {
                    Text(seriesDisplayName, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp)
                    Spacer(Modifier.height(8.dp))
                    val metaList = listOfNotNull(
                        year.takeIf { it.isNotBlank() },
                        "$totalSeasons temporadas",
                        genres.takeIf { it.isNotBlank() },
                        seriesItem?.voteAverage?.takeIf { it > 0f }?.let { "★ %.1f".format(it) }
                    )
                    Text(metaList.joinToString(" • "), color = Color.LightGray, fontSize = 16.sp)
                    focusedEpisode?.let { ep ->
                        val epMeta = buildList {
                            ep.seasonNumber?.let { s -> add("T$s") }
                            ep.episodeNumber?.let { e -> add("E$e") }
                            ep.airDate?.takeIf { it.isNotBlank() }?.let { add(formatSpanishDate(it) ?: it) }
                            ep.voteAverage?.let { if (it > 0) add("★ %.1f".format(it)) }
                        }
                        if (epMeta.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(epMeta.joinToString(" · "), color = Color(0xFFB0BEC5), fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = synopsis, 
                        color = Color.White, 
                        fontSize = 14.sp, 
                        maxLines = 5, 
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.heightIn(min = 100.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        var playFocused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .onFocusChanged { playFocused = it.isFocused }
                                .tvClickable { onEpisodeClick(uniqueEpisodes.firstOrNull { it.seasonNumber == selectedSeason } ?: allEpisodes.first(), allEpisodes, uniqueEpisodes) }
                                .background(if (playFocused) Color.LightGray else Color.White, androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(Modifier.width(8.dp))
                            Text("Reproducir", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(32.dp))
            }

            if (seasons.isNotEmpty()) {
                item {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(seasons) { season ->
                            val isSelected = season == selectedSeason
                            var chipFocused by remember { mutableStateOf(false) }
                            Text(
                                text = "Temporada $season",
                                color = when {
                                    isSelected -> Color.Black
                                    chipFocused -> Color.White
                                    else -> Color.LightGray
                                },
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .onFocusChanged { chipFocused = it.isFocused }
                                    .tvClickable {
                                        selectedSeason = season
                                        val idx = uniqueEpisodes.indexOfFirst { it.seasonNumber == season }
                                        if (idx >= 0) {
                                            coroutineScope.launch { episodesListState.animateScrollToItem(idx) }
                                        }
                                    }
                                    .background(
                                        if (isSelected) Color.White else if (chipFocused) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            item {
                androidx.compose.foundation.lazy.LazyRow(
                    state = episodesListState,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uniqueEpisodes, key = { it.stableId }) { ep ->
                        val epContentId = ep.providerId ?: ep.stableId
                        val wp = progressMap[epContentId] ?: progressMap[epContentId.substringAfterLast(":")]
                        EpisodeCard(
                            item = ep,
                            watchProgress = wp,
                            onClick = { onEpisodeClick(ep, allEpisodes, uniqueEpisodes) },
                            onFocus = {
                                focusedEpisode = ep
                                val epSeason = ep.seasonNumber ?: 1
                                if (epSeason != selectedSeason) {
                                    selectedSeason = epSeason
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeCard(
    item: CatalogItem,
    watchProgress: WatchProgressItem? = null,
    onClick: () -> Unit,
    onFocus: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    val isWatched = watchProgress?.isWatched == true
    val progressPercent = watchProgress?.progressPercent ?: 0
    val hasProgress = progressPercent in 1..99 && !isWatched

    Column(
        modifier = Modifier
            .width(240.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .onFocusChanged { 
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .tvClickable { onClick() }
            .border(
                width = 2.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .scale(if (isFocused) 1.03f else 1f)
            .background(Color.DarkGray.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.DarkGray)
        ) {
            val imageUrl = item.stillPath?.takeIf { it.isNotBlank() }
                ?: item.backdropUrl?.takeIf { it.isNotBlank() }
                ?: item.imageUrl.takeIf { it.isNotBlank() }
                ?: ""
            Log.d("EpisodeCard", "ep=${item.episodeNumber} imageUrl='$imageUrl' stillPath='${item.stillPath}' backdropUrl='${item.backdropUrl}'")
            if (imageUrl.isNotBlank()) {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    },
                    update = { iv ->
                        Glide.with(iv)
                            .load(imageUrl)
                            .into(iv)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            if (isWatched) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.7f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = item.episodeNumber?.toString() ?: "?", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            val runtime = item.runtimeMinutes
            val rating = item.voteAverage
            if ((runtime != null && runtime > 0) || (rating != null && rating > 0f)) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.7f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rating != null && rating > 0f) {
                        Text("★", color = Color(0xFFFFC107), fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("%.1f".format(rating), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if ((runtime != null && runtime > 0) && (rating != null && rating > 0f)) {
                        Spacer(Modifier.width(8.dp))
                    }
                    if (runtime != null && runtime > 0) {
                        Text("$runtime min", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isFocused) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
        }
        
        if (hasProgress) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressPercent / 100f)
                        .background(IptvAccent)
                )
            }
        }

        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = item.title,
                color = if (isFocused) Color.White else Color.LightGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun formatSpanishDate(dateString: String?): String? {
    if (dateString.isNullOrBlank()) return null
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val formatter = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("es", "ES"))
        val date = parser.parse(dateString)
        date?.let { formatter.format(it) } ?: dateString
    } catch (e: Exception) {
        dateString
    }
}
