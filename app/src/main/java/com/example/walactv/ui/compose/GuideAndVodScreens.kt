package com.example.walactv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.example.walactv.local.MovieEntity
import com.example.walactv.local.PagedContentLoader
import com.example.walactv.local.SeriesEntity
import com.example.walactv.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val ALL_OPTION = "Todos"

// ── Guide (TV / Events) ────────────────────────────────────────────────────

@Composable
internal fun GuideContent(fragment: ComposeMainFragment, kind: ContentKind) {
    val isEventGuide = kind == ContentKind.EVENT
    var selectedCountry by remember { mutableStateOf(ALL_OPTION) }
    var selectedGroup by remember { mutableStateOf(ALL_OPTION) }
    var searchQuery by remember { mutableStateOf("") }
    var showCountryDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    val lazyGridState = rememberLazyGridState()

    var eventItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    val loader = remember(kind) {
        PagedContentLoader(
            fragment.contentCacheManager,
            fragment.repository,
            kind
        )
    }
    var displayItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var totalCount by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(0) }
    var isLoadingPage by remember { mutableStateOf(false) }
    val pageSize = 50
    var filteredGroupOptions by remember { mutableStateOf<List<CatalogFilterOption>>(emptyList()) }

    val countryOptions = remember(kind, fragment.channelFilters) {
        if (isEventGuide) listOf(CatalogFilterOption(ALL_OPTION, "Todos"))
        else buildList {
            add(CatalogFilterOption(ALL_OPTION, "Todos"))
            fragment.channelFilters.countries.forEach(::add)
        }
    }

    LaunchedEffect(selectedCountry, fragment.channelFilters) {
        if (isEventGuide) return@LaunchedEffect
        val country = selectedCountry.takeUnless { it == ALL_OPTION }
        val groups = if (country != null) {
            fragment.contentCacheManager.getChannelsByCountry(country)
                .distinctBy { it.grupoNormalizado }.filter { it.grupoNormalizado.isNotBlank() }
                .map { CatalogFilterOption(it.grupoNormalizado, it.grupoNormalizado) }
        } else {
            fragment.channelFilters.groups.distinctBy { it.value }
                .filter { it.value != "Favorites" && it.value != "Favoritos" }
        }
        filteredGroupOptions =
            buildList { add(CatalogFilterOption(ALL_OPTION, "Todos")); addAll(groups) }
    }

    val groupOptions = if (isEventGuide) {
        remember(eventItems) {
            buildList {
                add(CatalogFilterOption(ALL_OPTION, "Todos"))
                eventItems.map { it.group.trim() }.filter(String::isNotBlank).distinct().sorted()
                    .forEach { add(CatalogFilterOption(it, it)) }
            }
        }
    } else filteredGroupOptions.ifEmpty {
        remember(fragment.channelFilters) {
            buildList {
                add(CatalogFilterOption(ALL_OPTION, "Todos"))
                fragment.channelFilters.groups.distinctBy { it.value }
                    .filter { it.value != "Favorites" && it.value != "Favoritos" }.forEach(::add)
            }
        }
    }

    val displayItemsForGrid = remember(displayItems) {
        if (isEventGuide) displayItems.sortedWith(compareBy<CatalogItem> { it.badgeText }.thenBy { it.title })
        else displayItems.sortedBy { it.channelNumber ?: Int.MAX_VALUE }
    }

    LaunchedEffect(displayItemsForGrid, fragment.currentItem) {
        if (!isEventGuide && displayItemsForGrid.isNotEmpty()) {
            val current = fragment.currentItem
            if (current != null) {
                val idx = displayItemsForGrid.indexOfFirst { it.stableId == current.stableId }
                if (idx > 0) lazyGridState.scrollToItem(maxOf(0, idx - 1))
            }
        }
    }

    LaunchedEffect(Unit) {
        val initial = fragment.guideInitialGroup ?: return@LaunchedEffect
        fragment.guideInitialGroup = null
        groupOptions.firstOrNull { it.value == initial || it.label == initial }
            ?.let { selectedGroup = it.value }
    }

    LaunchedEffect(kind) {
        if (isEventGuide) {
            runCatching { fragment.repository.loadEventsOnly() }.onSuccess { catalog ->
                eventItems = catalog.sections.flatMap { it.items }.distinctBy(CatalogItem::stableId)
                displayItems = eventItems; totalCount = displayItems.size
            }
        }
    }

    var lastLoadKey by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCountry, selectedGroup, searchQuery) {
        if (isEventGuide) return@LaunchedEffect
        val key = "$selectedCountry|$selectedGroup|$searchQuery"
        if (key == lastLoadKey || isLoading) return@LaunchedEffect
        lastLoadKey = key; isLoading = true
        loader.clear(); currentPage = 0; isLoadingPage = false
        if (searchQuery.isNotBlank()) {
            loader.loadSearch(searchQuery); displayItems = loader.getDisplayItems(); totalCount =
                loader.getTotalCount()
        } else {
            val country = selectedCountry.takeUnless { it == ALL_OPTION }
            val group = selectedGroup.takeUnless { it == ALL_OPTION }
            loader.refreshTotalCount(country, group); totalCount = loader.getTotalCount()
            loader.loadPage(0, country, group); displayItems = loader.getDisplayItems()
        }
        isLoading = false
    }

    LaunchedEffect(displayItemsForGrid) {
        if (isEventGuide && displayItemsForGrid.isNotEmpty()) {
            val index = fragment.findNextEventIndex(displayItemsForGrid)
            if (index > 0) lazyGridState.scrollToItem(index)
        }
    }

    LaunchedEffect(selectedCountry) {
        if (!isEventGuide) {
            searchQuery = ""; selectedGroup = ALL_OPTION
        }
    }

    LaunchedEffect(lazyGridState, searchQuery) {
        if (isEventGuide || searchQuery.isNotBlank()) return@LaunchedEffect
        snapshotFlow { lazyGridState.layoutInfo }
            .map { info ->
                (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
            }
            .distinctUntilChanged()
            .filter { (last, total) -> last >= 0 && total > 0 && last >= total - 10 }
            .collect {
                if (isLoadingPage || loader.isCurrentlyLoading()) return@collect
                val nextPage = currentPage + 1
                val maxPages = (totalCount + pageSize - 1) / pageSize
                if (nextPage >= maxPages || loader.isPageLoaded(nextPage)) return@collect
                isLoadingPage = true
                loader.loadPage(
                    nextPage,
                    selectedCountry.takeUnless { it == ALL_OPTION },
                    selectedGroup.takeUnless { it == ALL_OPTION })
                displayItems = loader.getDisplayItems(); currentPage = nextPage; isLoadingPage =
                false
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            title = screenTitle(kind),
            subtitle = if (!isEventGuide) "$totalCount canales disponibles" else ""
        )
        FilterTopBar(
            showIdioma = kind == ContentKind.CHANNEL,
            selectedIdioma = countryOptions.firstOrNull { it.value == selectedCountry }?.label
                ?: selectedCountry,
            selectedGrupo = groupOptions.firstOrNull { it.value == selectedGroup }?.label
                ?: selectedGroup,
            onIdiomaClicked = { showCountryDialog = true },
            onGrupoClicked = { showGroupDialog = true },
            idiomaFocusRequester = remember { FocusRequester() },
            grupoFocusRequester = remember { FocusRequester() },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            searchFocusRequester = remember { FocusRequester() },
            idiomaLabel = "País",
        )
        if (displayItemsForGrid.isEmpty() && !isLoadingPage) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isNotBlank()) "No hay resultados para \"$searchQuery\"" else "No hay contenido disponible",
                    color = IptvTextMuted,
                    fontSize = 18.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isEventGuide) 5 else 3),
                state = lazyGridState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(displayItemsForGrid.size) { index ->
                    val item = displayItemsForGrid[index]
                    if (isEventGuide) {
                        MediaCard(
                            item = item,
                            onFocused = {
                                fragment.selectedHero = item
                            }) { fragment.handleCardClick(item, displayItemsForGrid) }
                    } else {
                        EpgChannelCard(
                            item = item,
                            isCurrentChannel = fragment.currentItem?.stableId == item.stableId,
                            onFocused = {
                                fragment.selectedHero = item
                            }) { fragment.handleCardClick(item, displayItemsForGrid) }
                    }
                }
                if (isLoadingPage) item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Cargando...", color = IptvTextMuted, fontSize = 12.sp) }
                }
            }
        }
    }

    if (showCountryDialog) FilterDialog(
        title = "Selecciona país",
        options = countryOptions,
        selectedOption = selectedCountry,
        onOptionSelected = { selectedCountry = it.value; showCountryDialog = false },
        onDismiss = { showCountryDialog = false })
    if (showGroupDialog) FilterDialog(
        title = "Selecciona grupo",
        options = groupOptions,
        selectedOption = selectedGroup,
        onOptionSelected = { selectedGroup = it.value; showGroupDialog = false },
        onDismiss = { showGroupDialog = false })
}

// ── EPG channel card ───────────────────────────────────────────────────────

@Composable
internal fun EpgChannelCard(
    item: CatalogItem,
    isCurrentChannel: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor = when {
        isCurrentChannel && isFocused -> IptvAccent.copy(alpha = 0.35f); isCurrentChannel -> IptvAccent.copy(
            alpha = 0.18f
        ); isFocused -> IptvFocusBg; else -> IptvCard.copy(alpha = 0.7f)
    }
    val borderColor = when {
        isCurrentChannel -> IptvAccent; isFocused -> IptvFocusBorder; else -> IptvSurfaceVariant.copy(
            alpha = 0.5f
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(bgColor, RoundedCornerShape(10.dp))
            .border(
                if (isFocused || isCurrentChannel) 2.dp else 1.dp,
                borderColor,
                RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocused() }
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item.channelNumber?.let { num ->
            Text(
                num.toString().padStart(3, ' '),
                color = if (isCurrentChannel) IptvAccent else IptvTextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End
            )
        } ?: Box(modifier = Modifier.width(36.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(IptvSurfaceVariant, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center
        ) {
            if (item.imageUrl.isNotBlank()) RemoteImage(
                url = item.imageUrl,
                width = 80,
                height = 80,
                scaleType = ScaleType.FIT_CENTER
            )
            else Icon(
                Icons.Outlined.LiveTv,
                contentDescription = null,
                tint = IptvTextMuted,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                item.title,
                color = if (isCurrentChannel) IptvAccent else IptvTextPrimary,
                fontSize = 15.sp,
                fontWeight = if (isCurrentChannel) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.group.isNotBlank()) Text(
                item.group,
                color = IptvTextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrentChannel) Box(
            modifier = Modifier
                .background(IptvLive, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) { Text("▶", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
    }
}

// ── VOD grid ───────────────────────────────────────────────────────────────

@Composable
internal fun VodGridContent(fragment: ComposeMainFragment, kind: ContentKind) {
    var selectedCountry by remember { mutableStateOf(ALL_OPTION) }
    var selectedGroup by remember { mutableStateOf(ALL_OPTION) }
    var searchQuery by remember { mutableStateOf("") }
    var showCountryDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    val lazyGridState = rememberLazyGridState()

    val loader = remember(kind) {
        PagedContentLoader(
            fragment.contentCacheManager,
            fragment.repository,
            kind
        )
    }
    var displayItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var totalCount by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(0) }
    var isLoadingPage by remember { mutableStateOf(false) }
    val pageSize = 50

    val currentFilters =
        if (kind == ContentKind.MOVIE) fragment.movieFilters else fragment.seriesFilters
    val countryOptions = remember(currentFilters) {
        buildList {
            add(
                CatalogFilterOption(
                    ALL_OPTION,
                    "Todos"
                )
            ); currentFilters.countries.forEach(::add)
        }
    }
    var groupOptions by remember { mutableStateOf<List<CatalogFilterOption>>(emptyList()) }

    LaunchedEffect(selectedCountry, currentFilters) {
        val country = selectedCountry.takeUnless { it == ALL_OPTION }
        val groups: List<String> = if (country != null) {
            if (kind == ContentKind.MOVIE) {
                fragment.contentCacheManager.getMoviesByCountry(country)
            } else {
                @Suppress("UNCHECKED_CAST")
                (fragment.contentCacheManager.getSeriesByCountry(country) as List<MovieEntity>)
            }.distinctBy { it.grupoNormalizado }.filter { it.grupoNormalizado.isNotBlank() }
                .map { it.grupoNormalizado }
        } else {
            currentFilters.groups.distinctBy { it.value }
                .filter { it.value != "Favorites" && it.value != "Favoritos" }.map { it.value }
        }
        groupOptions = buildList {
            add(
                CatalogFilterOption(
                    ALL_OPTION,
                    "Todos"
                )
            ); addAll(groups.map { CatalogFilterOption(it, it) })
        }
    }

    LaunchedEffect(selectedCountry) { selectedGroup = ALL_OPTION }

    var lastLoadKey by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var searchDebounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(searchQuery) {
        searchDebounceJob?.cancel()
        searchDebounceJob = launch {
            kotlinx.coroutines.delay(500)
            val key = "$selectedCountry|$selectedGroup|$searchQuery"
            if (key == lastLoadKey) return@launch
            lastLoadKey = key; isLoading = true
            loader.clear(); currentPage = 0; isLoadingPage = false
            if (searchQuery.isNotBlank()) {
                loader.loadSearch(searchQuery); displayItems =
                    loader.getDisplayItems(); totalCount = loader.getTotalCount()
            } else {
                val country = selectedCountry.takeUnless { it == ALL_OPTION }
                val group = selectedGroup.takeUnless { it == ALL_OPTION }
                loader.refreshTotalCount(country, group); totalCount = loader.getTotalCount()
                loader.loadPage(0, country, group); displayItems = loader.getDisplayItems()
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedCountry, selectedGroup) {
        if (searchQuery.isNotBlank()) return@LaunchedEffect
        val key = "$selectedCountry|$selectedGroup|"
        if (key == lastLoadKey || isLoading) return@LaunchedEffect
        lastLoadKey = key; isLoading = true
        loader.clear(); currentPage = 0; isLoadingPage = false
        val country = selectedCountry.takeUnless { it == ALL_OPTION }
        val group = selectedGroup.takeUnless { it == ALL_OPTION }
        loader.refreshTotalCount(country, group); totalCount = loader.getTotalCount()
        loader.loadPage(0, country, group); displayItems = loader.getDisplayItems()
        isLoading = false
    }

    LaunchedEffect(lazyGridState, searchQuery) {
        if (searchQuery.isNotBlank()) return@LaunchedEffect
        snapshotFlow { lazyGridState.layoutInfo }
            .map { info ->
                (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
            }
            .distinctUntilChanged()
            .filter { (last, total) -> last >= 0 && total > 0 && last >= total - 10 }
            .collect {
                if (isLoadingPage || loader.isCurrentlyLoading()) return@collect
                val nextPage = currentPage + 1
                val maxPages = (totalCount + pageSize - 1) / pageSize
                if (nextPage >= maxPages || loader.isPageLoaded(nextPage)) return@collect
                isLoadingPage = true
                loader.loadPage(
                    nextPage,
                    selectedCountry.takeUnless { it == ALL_OPTION },
                    selectedGroup.takeUnless { it == ALL_OPTION })
                displayItems = loader.getDisplayItems(); currentPage = nextPage; isLoadingPage =
                false
            }
    }

    val displayItemsForGrid = remember(displayItems) { displayItems.sortedBy { it.title } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(title = screenTitle(kind), subtitle = "")
        FilterTopBar(
            showIdioma = true,
            selectedIdioma = countryOptions.firstOrNull { it.value == selectedCountry }?.label
                ?: selectedCountry,
            selectedGrupo = groupOptions.firstOrNull { it.value == selectedGroup }?.label
                ?: selectedGroup,
            onIdiomaClicked = { showCountryDialog = true },
            onGrupoClicked = { showGroupDialog = true },
            idiomaFocusRequester = remember { FocusRequester() },
            grupoFocusRequester = remember { FocusRequester() },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            searchFocusRequester = remember { FocusRequester() },
            idiomaLabel = "Idioma",
        )
        if (displayItemsForGrid.isEmpty() && !isLoadingPage) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isNotBlank()) "No hay resultados para \"$searchQuery\"" else "No hay contenido disponible",
                    color = IptvTextMuted,
                    fontSize = 18.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                state = lazyGridState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(displayItemsForGrid) { item ->
                    val wp = fragment.continueWatchingEntries[item.stableId]
                        ?: fragment.continueWatchingEntries[item.providerId.orEmpty()]
                        ?: item.providerId?.substringAfterLast(":")
                            ?.let { bareId ->
                                fragment.continueWatchingEntries["movie:$bareId"]
                                    ?: fragment.continueWatchingEntries["series:$bareId"]
                            }
                        ?: run {
                            val titleKey = when (item.kind) {
                                ContentKind.SERIES -> item.seriesName?.trim()?.lowercase()
                                ContentKind.MOVIE -> (item.normalizedTitle ?: item.title).trim()
                                    .lowercase()

                                else -> null
                            }
                            titleKey?.let { fragment.continueWatchingEntries["title:$it"] }
                        }
                    val itemWithWatched =
                        if (item.kind == ContentKind.MOVIE || item.kind == ContentKind.SERIES)
                            item.copy(isWatched = wp?.isWatched == true) else item
                    MediaCard(
                        item = itemWithWatched,
                        onFocused = { fragment.selectedHero = item }) {
                        fragment.handleCardClick(
                            item,
                            displayItemsForGrid
                        )
                    }
                }
                if (isLoadingPage) item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Cargando...", color = IptvTextMuted, fontSize = 14.sp) }
                }
            }
        }
    }

    if (showCountryDialog) FilterDialog(
        title = "Selecciona país",
        options = countryOptions,
        selectedOption = selectedCountry,
        onOptionSelected = { selectedCountry = it.value; showCountryDialog = false },
        onDismiss = { showCountryDialog = false })
    if (showGroupDialog) FilterDialog(
        title = "Selecciona grupo",
        options = groupOptions,
        selectedOption = selectedGroup,
        onOptionSelected = { selectedGroup = it.value; showGroupDialog = false },
        onDismiss = { showGroupDialog = false })
}
