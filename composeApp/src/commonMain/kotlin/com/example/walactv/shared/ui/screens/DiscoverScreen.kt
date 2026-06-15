package com.example.walactv.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.walactv.shared.domain.ContentKind
import com.example.walactv.shared.ui.theme.*

@Composable
fun DiscoverScreen(
    items: List<CatalogItem>,
    isLoading: Boolean,
    isDiscoverLoading: Boolean = false,
    onCardClick: (CatalogItem) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IptvBackground)
            .padding(24.dp),
    ) {
        if ((isLoading || isDiscoverLoading) && items.isEmpty()) {
            CircularProgressIndicator(color = IptvAccent, modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
