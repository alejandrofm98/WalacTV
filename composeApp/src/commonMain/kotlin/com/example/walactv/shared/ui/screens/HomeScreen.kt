package com.example.walactv.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.walactv.shared.domain.BrowseSection
import com.example.walactv.shared.domain.CatalogItem
import com.example.walactv.shared.domain.ContentKind
import com.example.walactv.shared.domain.preferredCardImageUrl
import com.example.walactv.shared.ui.theme.*

@Composable
fun HomeScreen(
    sections: List<BrowseSection>,
    continueWatching: BrowseSection?,
    selectedHero: CatalogItem?,
    isLoading: Boolean,
    onCardClick: (CatalogItem) -> Unit,
    onHeroClick: (CatalogItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IptvBackground)
            .padding(24.dp),
    ) {
        if (isLoading && sections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IptvAccent)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            selectedHero?.let { hero ->
                HeroBanner(
                    item = hero,
                    onClick = { onHeroClick(hero) },
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            continueWatching?.let { cwSection ->
                SectionRow(
                    title = cwSection.title,
                    items = cwSection.items,
                    onCardClick = onCardClick,
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            sections.forEach { section ->
                if (section.items.isNotEmpty()) {
                    SectionRow(
                        title = section.title,
                        items = section.items,
                        onCardClick = onCardClick,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroBanner(
    item: CatalogItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = IptvSurface),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, IptvBackground),
                        )
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp),
            ) {
                if (item.badgeText.isNotBlank()) {
                    Text(
                        text = item.badgeText,
                        color = IptvAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        color = IptvOnSurface,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionRow(
    title: String,
    items: List<CatalogItem>,
    onCardClick: (CatalogItem) -> Unit,
) {
    Column {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.stableId }) { item ->
                ContentCard(
                    item = item,
                    onClick = { onCardClick(item) },
                )
            }
        }
    }
}

@Composable
fun ContentCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = IptvSurface),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(IptvCard),
                contentAlignment = Alignment.Center,
            ) {
                if (item.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                if (item.kind == ContentKind.CHANNEL && item.channelNumber != null) {
                    Text(
                        text = "${item.channelNumber}",
                        color = IptvAccent,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.badgeText.isNotBlank()) {
                    Text(
                        text = item.badgeText,
                        color = IptvAccent,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
