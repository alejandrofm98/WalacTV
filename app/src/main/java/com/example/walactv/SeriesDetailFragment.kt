@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.example.walactv

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(displayEpisodes) { ep ->
                    EpisodeRow(ep) { onEpisodeClick(ep) }
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
fun EpisodeRow(item: CatalogItem, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isFocused) IptvFocusBg else IptvCard, RoundedCornerShape(8.dp))
            .border(1.dp, if (isFocused) IptvFocusBorder else IptvSurfaceVariant, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val epNum = item.episodeNumber?.toString() ?: "-"
        Text(epNum, color = if (isFocused) IptvTextPrimary else IptvTextMuted, fontSize = 18.sp, modifier = Modifier.width(40.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(item.title, color = IptvTextPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Episodio $epNum", color = IptvTextMuted, fontSize = 14.sp)
        }
    }
}
