package com.example.walactv.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Public
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import android.widget.ImageView.ScaleType.FIT_CENTER
import com.example.walactv.CatalogFilterOption
import com.example.walactv.CatalogItem
import com.example.walactv.ComposeMainFragment
import com.example.walactv.ContentKind
import com.example.walactv.local.PagedContentLoader
import com.example.walactv.local.parseCountryList
import com.example.walactv.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val ALL_OPTION = "Todos"
private const val FAVORITES_VALUE = "__favs__"
private const val TAG = "ChannelPickerDialog"

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
    var selectedCountryIndex by remember { mutableIntStateOf(0) }
    var selectedGroupIndex by remember { mutableIntStateOf(0) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var activePanel by remember { mutableIntStateOf(2) }

    val channelListState = rememberLazyListState()
    val groupListState = rememberLazyListState()
    val countryListState = rememberLazyListState()
    val dialogFocusRequester = remember { FocusRequester() }
    val dialogScope = rememberCoroutineScope()

    val loader = remember { PagedContentLoader(fragment.contentCacheManager, fragment.repository, ContentKind.CHANNEL) }
    var displayChannels by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(0) }
    var isLoadingPage by remember { mutableStateOf(false) }
    var totalCount by remember { mutableIntStateOf(0) }
    var isInitialLoading by remember { mutableStateOf(false) }
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
                .distinctBy { it.grupoNormalizado }
                .filter { it.grupoNormalizado.isNotBlank() }
                .map { CatalogFilterOption(it.grupoNormalizado, it.grupoNormalizado) }
        } else {
            fragment.channelFilters.groups
                .distinctBy { it.value }
                .filter { it.value != "Favorites" && it.value != "Favoritos" }
        }
        groupOptions = buildList {
            add(CatalogFilterOption(ALL_OPTION, "Todas las categorías"))
            add(CatalogFilterOption(FAVORITES_VALUE, "⭐ Favoritos"))
            addAll(groups)
        }
    }

    LaunchedEffect(countryOptions, currentCountry) {
        selectedCountryIndex = countryOptions.indexOfFirst { it.value == currentCountry }.coerceAtLeast(0)
    }

    LaunchedEffect(groupOptions, currentGroup, showFavorites) {
        selectedGroupIndex = groupOptions.indexOfFirst { option ->
            when (option.value) {
                FAVORITES_VALUE -> showFavorites
                ALL_OPTION -> !showFavorites && currentGroup == ALL_OPTION
                else -> !showFavorites && currentGroup == option.value
            }
        }.coerceAtLeast(0)
    }

    suspend fun performLoad() {
        try {
            loader.clear(); currentPage = 0; isLoadingPage = false
            when {
                searchQuery.isNotBlank() -> {
                    val country = currentCountry.takeUnless { it == ALL_OPTION }
                    val group = currentGroup.takeUnless { it == ALL_OPTION }
                    loader.loadSearch(searchQuery, country, group)
                    displayChannels = loader.getDisplayItems()
                    totalCount = displayChannels.size
                }
                showFavorites -> {
                    val favs = runCatching { fragment.repository.loadFavoriteChannels() }.getOrDefault(emptyList())
                    displayChannels = favs.sortedBy { it.channelNumber ?: Int.MAX_VALUE }
                    totalCount = displayChannels.size
                }
                else -> {
                    val country = currentCountry.takeUnless { it == ALL_OPTION }
                    val group = currentGroup.takeUnless { it == ALL_OPTION }
                    loader.refreshTotalCount(country, group)
                    totalCount = loader.getTotalCount()
                    val currentChannel = fragment.currentItem
                    if (currentChannel != null && currentChannel.kind == ContentKind.CHANNEL) {
                        val foundIndex = loader.loadUntilFound(currentChannel.stableId, country, group)
                        if (foundIndex >= 0) {
                            selectedIndex = foundIndex
                            activePanel = 2
                        } else {
                            loader.loadPage(0, country, group)
                            selectedIndex = 0
                        }
                    } else {
                        loader.loadPage(0, country, group)
                        selectedIndex = 0
                    }
                    displayChannels = loader.getDisplayItems()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "performLoad failed", e)
            displayChannels = emptyList()
            totalCount = 0
        } finally {
            isInitialLoading = false
        }
    }

    LaunchedEffect(Unit) {
        isInitialLoading = true
        if (fragment.currentItem?.kind == ContentKind.CHANNEL) {
            val currentChannel = fragment.currentItem ?: return@LaunchedEffect
            val entity = fragment.contentCacheManager.getChannelById(currentChannel.stableId)
            if (entity != null) {
                val channelCountries = entity.countries.parseCountryList()
                val targetCountry = channelCountries.firstOrNull { c -> countryOptions.any { it.value == c } } ?: ALL_OPTION
                val targetGroup = entity.grupoNormalizado.takeIf { it.isNotBlank() } ?: ALL_OPTION
                if (currentCountry != targetCountry) {
                    onCountryChange(targetCountry)
                }
                if (currentGroup != targetGroup && targetCountry != ALL_OPTION) {
                    onGroupChange(targetGroup)
                }
                if (showFavorites) onFavoritesChange(false)
            }
        }
        dialogScope.launch {
            delay(80.milliseconds)
            dialogFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(currentCountry, currentGroup, searchQuery, showFavorites) {
        performLoad()
    }

    LaunchedEffect(selectedIndex, displayChannels.size) {
        if (selectedIndex in displayChannels.indices) {
            delay(10.milliseconds)
            channelListState.scrollToItem(selectedIndex)
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) selectedIndex = 0
    }

    LaunchedEffect(activePanel) {
        when (activePanel) {
            0 -> if (countryOptions.isNotEmpty()) countryListState.scrollToItem(selectedCountryIndex)
            1 -> if (groupOptions.isNotEmpty()) groupListState.scrollToItem(selectedGroupIndex)
            2 -> if (selectedIndex in displayChannels.indices) channelListState.scrollToItem(selectedIndex)
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

    val panelBg = IptvSurface.copy(alpha = 0.92f)
    val activePanelBg = IptvCard.copy(alpha = 0.96f)
    val selectedItemBg = IptvFocusBg.copy(alpha = 0.55f)
    val dividerColor = IptvSurfaceVariant.copy(alpha = 0.7f)
    val accentColor = IptvAccent

    val alphaCountry = animateFloatAsState(targetValue = if (activePanel == 0) 1f else 0.85f, label = "countryAlpha")
    val alphaGroup = animateFloatAsState(targetValue = if (activePanel == 1) 1f else 0.85f, label = "groupAlpha")
    val alphaChannels = animateFloatAsState(targetValue = if (activePanel == 2) 1f else 0.85f, label = "channelsAlpha")

    Box(
        modifier = Modifier
            .fillMaxSize(0.92f)
            .shadow(24.dp, RoundedCornerShape(20.dp), clip = false)
            .background(IptvBackground.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
            .border(2.dp, IptvSurfaceVariant, RoundedCornerShape(20.dp))
            .focusRequester(dialogFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (activePanel) {
                    0 -> when (event.key) {
                        Key.DirectionUp -> {
                            if (selectedCountryIndex > 0) selectedCountryIndex--
                            true
                        }
                        Key.DirectionDown -> {
                            if (selectedCountryIndex < countryOptions.size - 1) selectedCountryIndex++
                            true
                        }
                        Key.DirectionRight -> {
                            activePanel = 1
                            true
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            countryOptions.getOrNull(selectedCountryIndex)?.let { option ->
                                onCountryChange(option.value)
                                onGroupChange(ALL_OPTION)
                                onFavoritesChange(false)
                                selectedGroupIndex = 0
                                activePanel = 1
                            }
                            true
                        }
                        Key.Back, Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                    1 -> when (event.key) {
                        Key.DirectionUp -> {
                            if (selectedGroupIndex > 0) selectedGroupIndex--
                            true
                        }
                        Key.DirectionDown -> {
                            if (selectedGroupIndex < groupOptions.size - 1) selectedGroupIndex++
                            true
                        }
                        Key.DirectionLeft -> {
                            activePanel = 0
                            true
                        }
                        Key.DirectionRight -> {
                            activePanel = 2
                            true
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            groupOptions.getOrNull(selectedGroupIndex)?.let { option ->
                                if (option.value == FAVORITES_VALUE) {
                                    onFavoritesChange(true)
                                } else {
                                    onFavoritesChange(false)
                                    onGroupChange(option.value)
                                }
                                selectedIndex = 0
                            }
                            true
                        }
                        Key.Back, Key.Escape -> {
                            activePanel = 0
                            true
                        }
                        else -> false
                    }
                    2 -> when (event.key) {
                        Key.DirectionUp -> {
                            if (selectedIndex > 0) selectedIndex--
                            true
                        }
                        Key.DirectionDown -> {
                            if (selectedIndex < displayChannels.size - 1) selectedIndex++
                            true
                        }
                        Key.DirectionLeft -> {
                            activePanel = 1
                            true
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            displayChannels.getOrNull(selectedIndex)?.let { onChannelSelected(it) }
                            true
                        }
                        Key.Back, Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                    else -> false
                }
            },
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Country panel
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(activePanelBg.copy(alpha = alphaCountry.value), RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .border(
                        width = if (activePanel == 0) 2.dp else 0.dp,
                        color = if (activePanel == 0) accentColor.copy(alpha = 0.6f) else Color.Transparent,
                        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                    )
            ) {
                PanelHeader(
                    title = "País",
                    isActive = activePanel == 0,
                    shape = RoundedCornerShape(topStart = 20.dp),
                    icon = {
                        Icon(
                            Icons.Outlined.Public,
                            contentDescription = null,
                            tint = if (activePanel == 0) IptvAccent else IptvTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                LazyColumn(
                    state = countryListState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(countryOptions, key = { _, item -> "c_${item.value}" }) { index, option ->
                        val isSelected = index == selectedCountryIndex
                        PanelListItem(
                            label = option.label,
                            isSelected = isSelected,
                            isActivePanel = activePanel == 0,
                            onClick = {
                                selectedCountryIndex = index
                                onCountryChange(option.value)
                                onGroupChange(ALL_OPTION)
                                onFavoritesChange(false)
                                selectedGroupIndex = 0
                                activePanel = 1
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(dividerColor))

            // Category panel
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .background(activePanelBg.copy(alpha = alphaGroup.value))
                    .border(
                        width = if (activePanel == 1) 2.dp else 0.dp,
                        color = if (activePanel == 1) accentColor.copy(alpha = 0.6f) else Color.Transparent,
                        shape = RoundedCornerShape(0.dp)
                    )
            ) {
                PanelHeader(
                    title = "Categoría",
                    isActive = activePanel == 1,
                    icon = {
                        Text(
                            "📂",
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 2.dp)
                        )
                    }
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                LazyColumn(
                    state = groupListState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(groupOptions, key = { _, item -> "g_${item.value}" }) { index, option ->
                        val isSelected = index == selectedGroupIndex
                        PanelListItem(
                            label = option.label,
                            isSelected = isSelected,
                            isActivePanel = activePanel == 1,
                            onClick = {
                                selectedGroupIndex = index
                                if (option.value == FAVORITES_VALUE) {
                                    onFavoritesChange(true)
                                } else {
                                    onFavoritesChange(false)
                                    onGroupChange(option.value)
                                }
                                selectedIndex = 0
                                activePanel = 2
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(dividerColor))

            // Channels panel
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(activePanelBg.copy(alpha = alphaChannels.value), RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
                    .border(
                        width = if (activePanel == 2) 2.dp else 0.dp,
                        color = if (activePanel == 2) accentColor.copy(alpha = 0.6f) else Color.Transparent,
                        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IptvSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(topEnd = 20.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.LiveTv, contentDescription = null, tint = if (activePanel == 2) accentColor else IptvTextSecondary, modifier = Modifier.size(18.dp))
                        Text("Canales", color = if (activePanel == 2) IptvTextPrimary else IptvTextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        val countText = when {
                            searchQuery.isNotBlank() -> "${displayChannels.size} resultados"
                            showFavorites -> "${displayChannels.size} ⭐"
                            else -> "$totalCount canales"
                        }
                        Text(countText, color = IptvTextMuted, fontSize = 12.sp)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                if (isInitialLoading) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Cargando guía...", color = IptvTextMuted, fontSize = 16.sp)
                    }
                } else if (displayChannels.isEmpty() && !isLoadingPage) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (searchQuery.isNotBlank()) "Sin resultados" else "No hay canales disponibles",
                            color = IptvTextMuted,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = channelListState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(displayChannels.size, key = { displayChannels[it].stableId }) { index ->
                            val item = displayChannels[index]
                            ChannelListItem(
                                item = item,
                                isHighlighted = index == selectedIndex,
                                isPlaying = fragment.currentItem?.stableId == item.stableId,
                                onClick = { onChannelSelected(item) }
                            )
                        }
                        if (isLoadingPage) item {
                            Box(modifier = Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                                Text("Cargando...", color = IptvTextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(
    title: String,
    isActive: Boolean,
    shape: RoundedCornerShape = RoundedCornerShape(0.dp),
    icon: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(IptvSurfaceVariant.copy(alpha = 0.5f), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.invoke()
            if (icon != null) Spacer(Modifier.width(10.dp))
            Text(title, color = if (isActive) IptvTextPrimary else IptvTextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PanelListItem(
    label: String,
    isSelected: Boolean,
    isActivePanel: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = when {
        isSelected && isActivePanel -> IptvAccent.copy(alpha = 0.25f)
        isSelected -> IptvFocusBg.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    val borderColor = when {
        isSelected && isActivePanel -> IptvAccent.copy(alpha = 0.8f)
        isSelected -> IptvFocusBorder.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected && isActivePanel -> IptvTextPrimary
        isSelected -> IptvTextPrimary
        else -> IptvTextSecondary
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(if (isSelected) IptvAccent else Color.Transparent, RoundedCornerShape(50))
            )
            Text(
                label,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChannelListItem(
    item: CatalogItem,
    isHighlighted: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val accentColor = IptvAccent
    val bgColor = when {
        isPlaying && isHighlighted -> accentColor.copy(alpha = 0.22f)
        isPlaying -> accentColor.copy(alpha = 0.12f)
        isHighlighted -> IptvFocusBg.copy(alpha = 0.65f)
        else -> Color.Transparent
    }
    val borderColor = when {
        isPlaying -> IptvAccent.copy(alpha = 0.9f)
        isHighlighted -> IptvFocusBorder.copy(alpha = 0.8f)
        else -> Color.Transparent
    }
    val titleColor = when {
        isPlaying -> IptvAccent
        isHighlighted -> IptvTextPrimary
        else -> IptvTextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (item.channelNumber != null) {
            Text(
                item.channelNumber.toString().padStart(3, ' '),
                color = if (isPlaying) IptvAccent else IptvTextMuted.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(30.dp),
                textAlign = TextAlign.End
            )
        } else {
            Box(modifier = Modifier.width(30.dp))
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .background(IptvSurfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (item.imageUrl.isNotBlank()) {
                RemoteImage(url = item.imageUrl, width = 80, height = 80, scaleType = FIT_CENTER)
            } else {
                Icon(Icons.Outlined.LiveTv, contentDescription = null, tint = IptvTextMuted.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                item.title,
                color = titleColor,
                fontSize = 14.sp,
                fontWeight = if (isHighlighted || isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.group.isNotBlank()) {
                Text(
                    item.group,
                    color = IptvTextMuted.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isPlaying) {
            Box(
                modifier = Modifier
                    .background(IptvLive.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text("EN DIRECTO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
