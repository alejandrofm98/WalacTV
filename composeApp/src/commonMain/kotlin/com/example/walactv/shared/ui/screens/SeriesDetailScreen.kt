package com.example.walactv.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walactv.shared.domain.CatalogItem
import com.example.walactv.shared.ui.theme.*

@Composable
fun SeriesDetailScreen(
    series: CatalogItem,
    episodes: List<CatalogItem>,
    isLoading: Boolean,
    onEpisodeClick: (CatalogItem) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IptvBackground)
            .padding(24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White,
                )
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = series.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (series.subtitle.isNotBlank()) {
                    Text(
                        text = series.subtitle,
                        color = IptvOnSurface,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IptvAccent)
            }
            return@Column
        }

        val grouped = episodes
            .groupBy { it.seasonNumber ?: 0 }
            .toSortedMap()

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            grouped.forEach { (season, seasonEpisodes) ->
                item(key = "season_header_$season") {
                    Text(
                        text = "Temporada $season",
                        color = IptvAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = if (season == grouped.keys.first()) 0.dp else 8.dp),
                    )
                }
                items(seasonEpisodes, key = { it.stableId }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        onClick = { onEpisodeClick(episode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: CatalogItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = IptvSurface),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildEpisodeLabel(episode.seasonNumber, episode.episodeNumber, episode.title),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (episode.description.isNotBlank()) {
                    Text(
                        text = episode.description,
                        color = IptvTextMuted,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (episode.badgeText.isNotBlank()) {
                Text(
                    text = episode.badgeText,
                    color = IptvAccent,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

private fun buildEpisodeLabel(season: Int?, episode: Int?, title: String): String {
    val prefix = if (season != null || episode != null) {
        "T${season ?: 0} E${episode ?: 0} - "
    } else ""
    return "$prefix$title"
}
