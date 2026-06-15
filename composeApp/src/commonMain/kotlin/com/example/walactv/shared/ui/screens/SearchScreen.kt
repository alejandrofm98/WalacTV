package com.example.walactv.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walactv.shared.domain.CatalogItem
import com.example.walactv.shared.ui.theme.*

@Composable
fun SearchScreen(
    query: String,
    results: List<CatalogItem>,
    isSearching: Boolean = false,
    onQueryChange: (String) -> Unit,
    onCardClick: (CatalogItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IptvBackground)
            .padding(24.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Buscar contenido") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IptvAccent,
                unfocusedBorderColor = IptvSurfaceVariant,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(20.dp))

        when {
            query.isBlank() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Escribe para buscar contenido",
                        color = IptvOnSurface,
                        fontSize = 16.sp,
                    )
                }
            }
            isSearching -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = IptvAccent)
                }
            }
            results.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No se encontraron resultados",
                        color = IptvOnSurface,
                        fontSize = 16.sp,
                    )
                }
            }
            else -> {
                Text(
                    text = "${results.size} resultados",
                    color = IptvOnSurface,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(results, key = { it.stableId }) { item ->
                        ContentCard(
                            item = item,
                            onClick = { onCardClick(item) },
                        )
                    }
                }
            }
        }
    }
}
