@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.example.walactv

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import com.bumptech.glide.Glide
import com.example.walactv.ui.theme.*

class SeriesDetailFragment : Fragment() {
    companion object {
        private const val ARG_SERIES_NAME = "series_name"
        fun newInstance(seriesName: String) = SeriesDetailFragment().apply {
            arguments = Bundle().apply { putString(ARG_SERIES_NAME, seriesName) }
        }
    }

    /** All episodes for this series, sorted by season then episode number. */
    private val sortedEpisodes: List<CatalogItem> by lazy {
        val seriesName = arguments?.getString(ARG_SERIES_NAME) ?: ""
        CatalogMemory.searchableItems
            .filter { it.kind == ContentKind.SERIES && it.seriesName == seriesName }
            .sortedWith(compareBy({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val seriesName = arguments?.getString(ARG_SERIES_NAME) ?: ""
        return ComposeView(requireContext()).apply {
            setContent {
                WalacTVTheme {
                    SeriesDetailScreen(seriesName) { item ->
                        playEpisode(item)
                    }
                }
            }
        }
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun playEpisode(item: CatalogItem) {
        val stream = item.streamOptions.firstOrNull() ?: return

        val currentIndex = sortedEpisodes.indexOfFirst { it.stableId == item.stableId }
        val nextEpisodeCallback: (() -> Unit)? = if (currentIndex >= 0 && currentIndex < sortedEpisodes.size - 1) {
            { playEpisode(sortedEpisodes[currentIndex + 1]) }
        } else {
            null
        }

        val playerFragment = PlayerFragment(
            streamUrl = stream.url,
            overlayNumber = item.kind.name,
            overlayTitle = item.title,
            overlayMeta = item.description.ifBlank { stream.label },
            contentKind = item.kind,
            onNavigateChannel = { false },
            onNavigateOption = { false },
            onDirectChannelNumber = { false },
            onToggleFavorite = { false },
            onOpenFavorites = { false },
            onOpenRecents = { false },
            onNextEpisode = nextEpisodeCallback,
        )
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, playerFragment)
            .addToBackStack(null)
            .commit()
    }
}

@Composable
fun SeriesDetailScreen(seriesName: String, onEpisodeClick: (CatalogItem) -> Unit) {
    val allEpisodes = remember(seriesName) {
        CatalogMemory.searchableItems
            .filter { it.kind == ContentKind.SERIES && it.seriesName == seriesName }
            .sortedBy { it.episodeNumber ?: Int.MAX_VALUE }
    }
    
    val seasons = remember(allEpisodes) {
        allEpisodes.mapNotNull { it.seasonNumber }.distinct().sorted()
    }
    
    var selectedSeason by remember { mutableStateOf(seasons.firstOrNull() ?: 1) }
    var showSeasonDialog by remember { mutableStateOf(false) }
    val seasonFocusRequester = remember { FocusRequester() }

    val displayEpisodes = remember(selectedSeason, allEpisodes) {
        allEpisodes.filter { (it.seasonNumber ?: 1) == selectedSeason }
    }

    val posterUrl = allEpisodes.firstOrNull()?.imageUrl ?: ""

    Box(modifier = Modifier.fillMaxSize().background(IptvBackground).padding(32.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (posterUrl.isNotBlank()) {
                    AndroidView(
                        factory = { context ->
                            ImageView(context).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                            }
                        },
                        update = { imageView ->
                            Glide.with(imageView)
                                .load(posterUrl)
                                .override(300, 450)
                                .into(imageView)
                        },
                        modifier = Modifier.width(100.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(modifier = Modifier.width(100.dp).fillMaxHeight().background(IptvSurfaceVariant, RoundedCornerShape(8.dp)))
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Text(seriesName, color = IptvTextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("${seasons.size} Temporadas • ${allEpisodes.size} Episodios", color = IptvTextMuted, fontSize = 16.sp)
                    
                    Spacer(Modifier.height(8.dp))
                    
                    if (seasons.isNotEmpty()) {
                        FilterTopBarButton(
                            label = "Temporada $selectedSeason",
                            onClick = { showSeasonDialog = true },
                            focusRequester = seasonFocusRequester
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(displayEpisodes, key = { it.stableId }) { ep ->
                    EpisodeCard(ep) { onEpisodeClick(ep) }
                }
            }
        }

        if (showSeasonDialog) {
            FilterDialog(
                title = "Selecciona Temporada",
                options = seasons.map { "Temporada $it" },
                selectedOption = "Temporada $selectedSeason",
                onOptionSelected = {
                    selectedSeason = it.removePrefix("Temporada ").toIntOrNull() ?: 1
                    showSeasonDialog = false
                },
                onDismiss = { showSeasonDialog = false }
            )
        }
        
        LaunchedEffect(showSeasonDialog) {
            if (!showSeasonDialog) {
                runCatching { seasonFocusRequester.requestFocus() }
            }
        }
    }
}

@Composable
fun EpisodeCard(item: CatalogItem, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(item.imageUrl) {
        currentUrl = item.imageUrl
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) IptvFocusBg else IptvCard)
            .border(
                width = 2.dp,
                color = if (isFocused) IptvFocusBorder else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.03f else 1f)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(IptvSurfaceVariant)
        ) {
            val imageUrl = currentUrl
            if (!imageUrl.isNullOrBlank()) {
                var imageView by remember { mutableStateOf<ImageView?>(null) }
                
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            imageView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                LaunchedEffect(imageView, imageUrl) {
                    imageView?.let { iv ->
                        Glide.with(iv)
                            .load(imageUrl)
                            .override(300, 169)
                            .into(iv)
                    }
                }
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(
                        Color.Black.copy(alpha = 0.7f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val epNum = item.episodeNumber?.toString() ?: "?"
                Text(
                    text = "EP $epNum",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = item.title,
            color = if (isFocused) IptvTextPrimary else IptvTextMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
