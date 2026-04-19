package com.example.walactv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import android.widget.ImageView.ScaleType
import com.example.walactv.CatalogFilterOption
import com.example.walactv.CatalogItem
import com.example.walactv.ComposeMainFragment
import com.example.walactv.ContentKind
import com.example.walactv.local.PagedContentLoader
import com.example.walactv.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val ALL_OPTION = "Todos"

@Composable
internal fun ChannelPickerDialog(
    fragment: ComposeMainFragment,
    currentCountry: String,
    currentGroup: String,
    searchQuery: String,
    showFavorites: Boolean,
    onCountryChange: (String) -> Unit,
    onGroupChange: (String) -> Unit,
    onFavoritesChange: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onChannelSelected: (CatalogItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIndex by remember { mutableStateOf(0) }
    var activePanel by remember { mutableStateOf(2) }

    val channelListState = rememberLazyListState()
    val groupListState = rememberLazyListState()
    val listFocusRequester = remember { FocusRequester() }
    val groupFocusRequester = remember { FocusRequester() }

    val loader = remember { PagedContentLoader(fragment.contentCacheManager, fragment.repository, ContentKind.CHANNEL) }
    var displayChannels by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var currentPage by remember { mutableStateOf(0) }
    var isLoadingPage by remember { mutableStateOf(false) }
    var totalCount by remember { mutableStateOf(0) }
    val pageSize = 50

    val countryOptions = remember(fragment.channelFilters) {
        buildList {
            add(CatalogFilterOption(ALL_OPTION, "Todos los países"))
            fragment.channelFilters.countries.forEach(::add)
        }
    }

    var groupOptions by remember { mutableStateOf<List<CatalogFilterOption>>(emptyList()) }

    LaunchedEffect(currentCountry) {
        val country = currentCountry.takeUnless { it == ALL_OPTION }
        val groups = if (country != null) {
            fragment.contentCacheManager.getChannelsByCountry(country)
                .distinctBy { it.grupoNormalizado }.filter { it.grupoNormalizado.isNotBlank() }
                .map { CatalogFilterOption(it.grupoNormalizado, it.grupoNormalizado) }
        } else {
            fragment.channelFilters.groups.distinctBy { it.value }.filter { it.value != "Favorites" && it.value != "Favoritos" }
        }
        groupOptions = buildList {
            add(CatalogFilterOption(ALL_OPTION, "Todas las categorías"))
            add(CatalogFilterOption("__favs__", "⭐ Favoritos"))
            addAll(groups)
        }
    }

    LaunchedEffect(currentCountry, currentGroup, searchQuery, showFavorites) {
        loader.clear(); currentPage = 0; isLoadingPage = false
        when {
            searchQuery.isNotBlank() -> {
                val country = currentCountry.takeUnless { it == ALL_OPTION }
                val group = currentGroup.takeUnless { it == ALL_OPTION }
                loader.loadSearch(searchQuery, country, group); displayChannels = loader.getDisplayItems(); totalCount = displayChannels.size
            }
            showFavorites -> {
                val favs = runCatching { fragment.repository.loadFavoriteChannels() }.getOrDefault(emptyList())
                displayChannels = favs.sortedBy { it.channelNumber ?: Int.MAX_VALUE }; totalCount = displayChannels.size
            }
            else -> {
                val country = currentCountry.takeUnless { it == ALL_OPTION }
                val group = currentGroup.takeUnless { it == ALL_OPTION }
                loader.refreshTotalCount(country, group); totalCount = loader.getTotalCount()
                loader.loadPage(0, country, group); displayChannels = loader.getDisplayItems()
            }
        }
        selectedIndex = fragment.currentItem?.let { c -> displayChannels.indexOfFirst { it.stableId == c.stableId }.coerceAtLeast(0) } ?: 0
    }

    LaunchedEffect(selectedIndex, displayChannels.size) {
        if (selectedIndex in displayChannels.indices) {
            kotlinx.coroutines.delay(10); channelListState.scrollToItem(selectedIndex)
        }
    }

    LaunchedEffect(channelListState, searchQuery, showFavorites) {
        if (searchQuery.isNotBlank() || showFavorites) return@LaunchedEffect
        snapshotFlow { channelListState.layoutInfo }
            .map { info -> (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount }
            .distinctUntilChanged()
            .filter { (last, total) -> last >= 0 && total > 0 && last >= total - 10 }
            .collect {
                if (isLoadingPage || loader.isCurrentlyLoading()) return@collect
                val nextPage = currentPage + 1
                val maxPages = (totalCount + pageSize - 1) / pageSize
                if (nextPage >= maxPages || loader.isPageLoaded(nextPage)) return@collect
                isLoadingPage = true
                loader.loadPage(nextPage, currentCountry.takeUnless { it == ALL_OPTION }, currentGroup.takeUnless { it == ALL_OPTION })
                displayChannels = loader.getDisplayItems(); currentPage = nextPage; isLoadingPage = false
            }
    }

    val panelBg = Color(0xFF0D0D1E).copy(alpha = 0.92f)
    val activePanelBg = Color(0xFF13132B).copy(alpha = 0.96f)
    val selectedItemBg = Color(0xFF2A2A6E).copy(alpha = 0.85f)
    val dividerColor = Color(0xFF2D2D60).copy(alpha = 0.6f)
    val accentColor = IptvAccent
    val borderGlow = Color(0xFF4444AA).copy(alpha = 0.5f)

    Box(
        modifier = Modifier.fillMaxSize(0.90f)
            .background(Color(0xFF080818).copy(alpha = 0.88f), RoundedCornerShape(16.dp))
            .border(1.dp, borderGlow, RoundedCornerShape(16.dp)),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Country panel
            Column(
                modifier = Modifier.width(200.dp).fillMaxHeight()
                    .background(if (activePanel == 0) activePanelBg else panelBg, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            ) {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A40).copy(alpha = 0.7f), RoundedCornerShape(topStart = 16.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🌍", fontSize = 16.sp)
                        Text("País", color = if (activePanel == 0) accentColor else IptvTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(countryOptions, key = { "c_${it.value}" }) { option ->
                        val isSelected = currentCountry == option.value
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) selectedItemBg else Color.Transparent)
                                .border(if (isSelected) 1.dp else 0.dp, if (isSelected) accentColor.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable {
                                    onCountryChange(option.value); onGroupChange(ALL_OPTION); onFavoritesChange(false); activePanel = 1
                                    kotlinx.coroutines.GlobalScope.launch { kotlinx.coroutines.delay(50); runCatching { groupFocusRequester.requestFocus() } }
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isSelected) Box(modifier = Modifier.size(6.dp).background(accentColor, RoundedCornerShape(50))) else Spacer(modifier = Modifier.size(6.dp))
                                Text(option.label, color = if (isSelected) IptvTextPrimary else IptvTextMuted, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(dividerColor))

            // Category panel
            Column(modifier = Modifier.width(210.dp).fillMaxHeight().background(if (activePanel == 1) activePanelBg else panelBg)) {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A40).copy(alpha = 0.7f)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📂", fontSize = 16.sp)
                        Text("Categoría", color = if (activePanel == 1) accentColor else IptvTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                LazyColumn(state = groupListState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(groupOptions, key = { "g_${it.value}" }) { option ->
                        val isSelected = if (option.value == "__favs__") showFavorites else (!showFavorites && currentGroup == option.value)
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) selectedItemBg else Color.Transparent)
                                .border(if (isSelected) 1.dp else 0.dp, if (isSelected) accentColor.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable {
                                    if (option.value == "__favs__") onFavoritesChange(true) else { onFavoritesChange(false); onGroupChange(option.value) }
                                    selectedIndex = 0; activePanel = 2
                                    kotlinx.coroutines.GlobalScope.launch { kotlinx.coroutines.delay(50); runCatching { listFocusRequester.requestFocus() } }
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isSelected) Box(modifier = Modifier.size(6.dp).background(accentColor, RoundedCornerShape(50))) else Spacer(modifier = Modifier.size(6.dp))
                                Text(option.label, color = if (isSelected) IptvTextPrimary else IptvTextMuted, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(dividerColor))

            // Channels panel
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(if (activePanel == 2) activePanelBg else panelBg, RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
            ) {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A40).copy(alpha = 0.7f), RoundedCornerShape(topEnd = 16.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.LiveTv, contentDescription = null, tint = if (activePanel == 2) accentColor else IptvTextSecondary, modifier = Modifier.size(16.dp))
                        Text("Canales", color = if (activePanel == 2) accentColor else IptvTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(if (searchQuery.isNotBlank()) "${displayChannels.size}" else if (showFavorites) "${displayChannels.size} ⭐" else "$totalCount", color = IptvTextMuted, fontSize = 11.sp)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().focusable()
                        .onFocusChanged { if (it.hasFocus) activePanel = 2 }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionUp    -> { if (selectedIndex > 0) selectedIndex--; true }
                                Key.DirectionDown  -> { if (selectedIndex < displayChannels.size - 1) selectedIndex++; true }
                                Key.DirectionLeft  -> { activePanel = 1; true }
                                Key.Enter, Key.DirectionCenter -> { displayChannels.getOrNull(selectedIndex)?.let { onChannelSelected(it) }; true }
                                Key.Back, Key.Escape -> { onDismiss(); true }
                                else -> false
                            }
                        }
                ) {
                    if (displayChannels.isEmpty() && !isLoadingPage) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (searchQuery.isNotBlank()) "Sin resultados" else "No hay canales disponibles", color = IptvTextMuted, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(state = channelListState, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(displayChannels.size) { index ->
                                val item = displayChannels[index]
                                val isHighlighted = index == selectedIndex
                                val isPlaying = fragment.currentItem?.stableId == item.stableId
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .background(when { isPlaying && isHighlighted -> accentColor.copy(alpha = 0.28f); isPlaying -> accentColor.copy(alpha = 0.14f); isHighlighted -> selectedItemBg; else -> Color.Transparent })
                                        .border(if (isPlaying || isHighlighted) 1.dp else 0.dp, when { isPlaying -> accentColor.copy(alpha = 0.7f); isHighlighted -> IptvFocusBorder.copy(alpha = 0.6f); else -> Color.Transparent }, RoundedCornerShape(8.dp))
                                        .clickable { onChannelSelected(item) }.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    item.channelNumber?.let { num ->
                                        Text(num.toString().padStart(3, ' '), color = if (isPlaying) accentColor else IptvTextMuted.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp), textAlign = TextAlign.End)
                                    } ?: Box(modifier = Modifier.width(26.dp))
                                    Box(modifier = Modifier.size(34.dp).background(Color(0xFF1A1A40).copy(alpha = 0.8f), RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                        if (item.imageUrl.isNotBlank()) RemoteImage(url = item.imageUrl, width = 64, height = 64, scaleType = ScaleType.FIT_CENTER)
                                        else Icon(Icons.Outlined.LiveTv, contentDescription = null, tint = IptvTextMuted.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                        Text(item.title, color = when { isPlaying -> accentColor; isHighlighted -> IptvTextPrimary; else -> IptvTextSecondary }, fontSize = 13.sp, fontWeight = if (isHighlighted || isPlaying) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (item.group.isNotBlank()) Text(item.group, color = IptvTextMuted.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (isPlaying) Box(modifier = Modifier.background(IptvLive.copy(alpha = 0.85f), RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) { Text("▶", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                            if (isLoadingPage) item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Text("Cargando...", color = IptvTextMuted, fontSize = 12.sp) } }
                        }
                    }
                }
            }
        }
    }
}
