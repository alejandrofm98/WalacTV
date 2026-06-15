package com.example.walactv.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import com.example.walactv.shared.ui.theme.*

@Composable
fun MobileHomeScreen(
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (isLoading && sections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IptvAccent)
            }
            return@Column
        }

        selectedHero?.let { hero ->
            MobileHeroCard(item = hero, onClick = { onHeroClick(hero) })
            Spacer(modifier = Modifier.height(20.dp))
        }

        continueWatching?.let { cw ->
            MobileSection(title = cw.title, items = cw.items, onCardClick = onCardClick)
            Spacer(modifier = Modifier.height(16.dp))
        }

        sections.forEach { section ->
            if (section.items.isNotEmpty()) {
                MobileSection(title = section.title, items = section.items, onCardClick = onCardClick)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MobileHeroCard(item: CatalogItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
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
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, IptvBackground))
                ),
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            ) {
                if (item.badgeText.isNotBlank()) {
                    Text(item.badgeText, color = IptvAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Text(item.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, color = IptvOnSurface, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MobileSection(title: String, items: List<CatalogItem>, onCardClick: (CatalogItem) -> Unit) {
    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.stableId }) { item ->
            MobileContentCard(item = item, onClick = { onCardClick(item) })
        }
    }
}

@Composable
fun MobileContentCard(item: CatalogItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(130.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = IptvSurface),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp).background(IptvCard),
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
                    Text("${item.channelNumber}", color = IptvAccent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.padding(6.dp)) {
                Text(item.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
