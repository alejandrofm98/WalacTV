package com.example.walactv.ui.compose

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.example.walactv.data.model.CatalogFilters
import android.widget.ImageView.ScaleType.FIT_CENTER
import com.example.walactv.data.remote.api.dto.FilterOptionDto
import com.example.walactv.data.model.CatalogItem
import com.example.walactv.ui.fragment.ComposeMainFragment
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.model.searchableText
import com.example.walactv.local.PagedContentLoader
import com.example.walactv.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val ALL_OPTION = "Todos"

// ── Guide (TV / Events) ────────────────────────────────────────────────────

@Composable
internal fun GuideContent(fragment: ComposeMainFragment, kind: ContentKind) {
    val isEventGuide = kind == ContentKind.EVENT
    val gridColumns = if (isEventGuide) 4 else 3
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
    var totalCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var isLoadingPage by remember { mutableStateOf(false) }
    val pageSize = 50
    var filteredGroupOptions by remember { mutableStateOf<List<FilterOptionDto>>(emptyList()) }
    var forceFocusFirstItem by remember { mutableStateOf(false) }

    val countryOptions = remember(kind, fragment.channelFilters) {
        if (isEventGuide) listOf(FilterOptionDto(ALL_OPTION, "Todos"))
        else buildList {
            add(FilterOptionDto(ALL_OPTION, "Todos"))
            fragment.channelFilters.countries.forEach(::add)
        }
    }

    LaunchedEffect(selectedCountry, fragment.channelFilters) {
        if (isEventGuide) return@LaunchedEffect
        val country = selectedCountry.takeUnless { it == ALL_OPTION }
        val groups = if (country != null) {
            fragment.contentCacheManager.getChannelsByCountry(country)
                .distinctBy { it.grupoNormalizado }.filter { it.grupoNormalizado.isNotBlank() }
                .map { FilterOptionDto(it.grupoNormalizado, it.grupoNormalizado) }
        } else {
            fragment.channelFilters.groups.distinctBy { it.value }
                .filter { it.value != "Favorites" && it.value != "Favoritos" }
        }
        filteredGroupOptions =
            buildList { add(FilterOptionDto(ALL_OPTION, "Todos")); addAll(groups) }
    }

    val groupOptions = if (isEventGuide) {
        remember(eventItems) {
            buildList {
                add(FilterOptionDto(ALL_OPTION, "Todos"))
                eventItems.map { it.group.trim() }.filter(String::isNotBlank).distinct().sorted()
                    .forEach { add(FilterOptionDto(it, it)) }
            }
        }
    } else filteredGroupOptions.ifEmpty {
        remember(fragment.channelFilters) {
            buildList {
                add(FilterOptionDto(ALL_OPTION, "Todos"))
                fragment.channelFilters.groups.distinctBy { it.value }
                    .filter { it.value != "Favorites" && it.value != "Favoritos" }.forEach(::add)
            }
        }
    }

    val displayItemsForGrid = remember(displayItems) {
        if (isEventGuide) displayItems.sortedWith(compareBy<CatalogItem> { it.badgeText }.thenBy { it.title })
        else displayItems.sortedBy { it.channelNumber ?: Int.MAX_VALUE }
    }
    val itemFocusRequesters = remember(displayItemsForGrid.size) {
        List(displayItemsForGrid.size) { FocusRequester() }
    }

    LaunchedEffect(fragment.contentFocusTrigger) {
        if (fragment.contentFocusTrigger == 0 || displayItemsForGrid.isEmpty()) return@LaunchedEffect
        if (forceFocusFirstItem) return@LaunchedEffect
        Log.d("MainShellFocus", "guide contentFocusTrigger searchQuery='$searchQuery' -> focusing first item kind=$kind")
        runCatching {
            lazyGridState.scrollToItem(0)
            delay(80.milliseconds)
            itemFocusRequesters.firstOrNull()?.requestFocus()
        }.onSuccess {
            Log.d("MainShellFocus", "guide first item requestFocus success kind=$kind items=${displayItemsForGrid.size}")
        }.onFailure {
            Log.w("MainShellFocus", "guide first item requestFocus failed kind=$kind: ${it.message}")
        }
    }

    LaunchedEffect(fragment.searchBackTrigger) {
        if (fragment.searchBackTrigger == 0) return@LaunchedEffect
        Log.d("FocusTrace", "searchBackTrigger fired for $kind -> forceFocus")
        forceFocusFirstItem = true
    }

    LaunchedEffect(forceFocusFirstItem) {
        if (!forceFocusFirstItem) return@LaunchedEffect
        var retries = 3
        while (retries-- > 0 && displayItemsForGrid.isEmpty()) {
            delay(100.milliseconds)
        }
        if (displayItemsForGrid.isEmpty()) return@LaunchedEffect
        Log.d("FocusTrace", "forceFocusFirstItem EXECUTING -> scrollToItem(0) + requestFocus kind=$kind")
        lazyGridState.scrollToItem(0)
        delay(50.milliseconds)
        itemFocusRequesters.firstOrNull()?.requestFocus()
        forceFocusFirstItem = false
        Log.d("FocusTrace", "forceFocusFirstItem DONE kind=$kind")
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

    LaunchedEffect(selectedCountry, selectedGroup, searchQuery) {
        if (isEventGuide) {
            delay(300.milliseconds)
            val group = selectedGroup.takeUnless { it == ALL_OPTION }
            val query = searchQuery.takeIf { it.isNotBlank() }?.trim()?.lowercase()
            displayItems = eventItems.filter { item ->
                (group == null || item.group.trim() == group) &&
                (query == null || item.searchableText().joinToString(" ").lowercase().contains(query))
            }
            return@LaunchedEffect
        }
        if (searchQuery.isNotBlank()) delay(300.milliseconds)
        val key = "$selectedCountry|$selectedGroup|$searchQuery"
        if (key == lastLoadKey) return@LaunchedEffect
        Log.d("GuideContent", "filter changed for $kind: key=$key, clearing and reloading")
        loader.clear(); currentPage = 0; isLoadingPage = false
        if (searchQuery.isNotBlank()) {
            val country = selectedCountry.takeUnless { it == ALL_OPTION }
            val group = selectedGroup.takeUnless { it == ALL_OPTION }
            loader.loadSearch(searchQuery, country, group)
        } else {
            val country = selectedCountry.takeUnless { it == ALL_OPTION }
            val group = selectedGroup.takeUnless { it == ALL_OPTION }
            loader.refreshTotalCount(country, group)
            loader.loadPage(0, country, group)
        }
        lastLoadKey = key
        displayItems = loader.getDisplayItems()
        totalCount = loader.getTotalCount()
        Log.d("GuideContent", "filter load complete for $kind: displayItems=${displayItems.size}, totalCount=$totalCount")
    }

    LaunchedEffect(displayItemsForGrid) {
        if (isEventGuide && displayItemsForGrid.isNotEmpty()) {
            val index = fragment.findNextEventIndex(displayItemsForGrid)
            if (index > 0) lazyGridState.scrollToItem(index)
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
                Log.d("GuideContent", "pagination trigger for $kind: loading page=$nextPage (currentPage=$currentPage, maxPages=$maxPages)")
                isLoadingPage = true
                loader.loadPage(
                    nextPage,
                    selectedCountry.takeUnless { it == ALL_OPTION },
                    selectedGroup.takeUnless { it == ALL_OPTION })
                displayItems = loader.getDisplayItems(); currentPage = nextPage; isLoadingPage =
                false
                Log.d("GuideContent", "page $nextPage loaded for $kind: cache.size=${displayItems.size}")
        }
    }

    LaunchedEffect(forceFocusFirstItem) {
        if (!forceFocusFirstItem) return@LaunchedEffect
        var retries = 3
        while (retries-- > 0 && displayItemsForGrid.isEmpty()) {
            delay(100.milliseconds)
        }
        if (displayItemsForGrid.isEmpty()) return@LaunchedEffect
        Log.d("FocusTrace", "forceFocusFirstItem EXECUTING -> scrollToItem(0) + requestFocus kind=$kind")
        lazyGridState.scrollToItem(0)
        delay(50.milliseconds)
        itemFocusRequesters.firstOrNull()?.requestFocus()
        forceFocusFirstItem = false
        Log.d("FocusTrace", "forceFocusFirstItem DONE kind=$kind")
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
            onSearchImeDismissed = { Log.d("FocusTrace", "onSearchImeDismissed CALLED kind=$kind"); forceFocusFirstItem = true },
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
                columns = GridCells.Fixed(gridColumns),
                state = lazyGridState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(displayItemsForGrid.size, key = { displayItemsForGrid[it].stableId }) { index ->
                    val item = displayItemsForGrid[index]
                    if (isEventGuide) {
                        val isLive = item.badgeText.matches(Regex("\\d{1,2}:\\d{2}.*")) ||
                            item.badgeText.contains("LIVE", ignoreCase = true) ||
                            item.badgeText.contains("EN VIVO", ignoreCase = true)
                        EventVsCard(
                            item = item,
                            modifier = Modifier.focusRequester(itemFocusRequesters[index]),
                            isLive = isLive,
                            useFixedWidth = false,
                            channelLineup = fragment.channelLineup,
                            onFocused = {
                                fragment.contentFocusCanOpenRail = index % gridColumns == 0
                                fragment.selectedHero = item
                            }) { fragment.handleCardClick(item, displayItemsForGrid) }
                    } else {
                        EpgChannelCard(
                            item = item,
                            isCurrentChannel = fragment.currentItem?.stableId == item.stableId,
                            modifier = Modifier.focusRequester(itemFocusRequesters[index]),
                            onFocused = {
                                fragment.contentFocusCanOpenRail = index % gridColumns == 0
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
    modifier: Modifier = Modifier,
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
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(bgColor, RoundedCornerShape(10.dp))
            .border(
                if (isFocused || isCurrentChannel) 2.dp else 1.dp,
                borderColor,
                RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocused() }
            .tvClickable { onClick() }
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
                scaleType = FIT_CENTER
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
    val gridColumns = 5
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
    var totalCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var isLoadingPage by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val pageSize = 50

    val currentFilters =
        if (kind == ContentKind.MOVIE) fragment.movieFilters else fragment.seriesFilters
    val countryOptions = remember(currentFilters) {
        buildList {
            add(
                FilterOptionDto(
                    ALL_OPTION,
                    "Todos"
                )
            ); currentFilters.countries.forEach(::add)
        }
    }
    var groupOptions by remember { mutableStateOf<List<FilterOptionDto>>(emptyList()) }
    var forceFocusFirstItem by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCountry, currentFilters) {
        val country = selectedCountry.takeUnless { it == ALL_OPTION }
        val filters = if (country != null) {
            runCatching { fragment.repository.loadCatalogFilters(kind, country) }
                .getOrElse { currentFilters }
        } else currentFilters
        val groups = filters.groups.distinctBy { it.value }
            .filter { it.value != "Favorites" && it.value != "Favoritos" }
        groupOptions = buildList {
            add(FilterOptionDto(ALL_OPTION, "Todos"))
            addAll(groups)
        }
    }

    LaunchedEffect(selectedCountry) { selectedGroup = ALL_OPTION }

    var lastLoadKey by remember { mutableStateOf("") }

    LaunchedEffect(selectedCountry, selectedGroup, searchQuery) {
        val key = "$selectedCountry|$selectedGroup|$searchQuery"
        if (key == lastLoadKey) return@LaunchedEffect
        Log.d("VodGrid", "filter changed for $kind: key=$key, cancelling and reloading")
        loader.clear(); currentPage = 0; isLoadingPage = false
        if (searchQuery.isNotBlank()) {
            delay(300.milliseconds)
        }
        lastLoadKey = key
        val country = selectedCountry.takeUnless { it == ALL_OPTION }
        val group = selectedGroup.takeUnless { it == ALL_OPTION }
        loadError = null
        runCatching {
            if (searchQuery.isNotBlank()) {
                loader.loadSearch(searchQuery, country, group)
            } else {
                loader.refreshTotalCount(country, group)
                loader.loadPage(0, country, group)
            }
        }.onFailure {
            loadError = it.message ?: "No se pudo cargar el contenido"
        }
        totalCount = loader.getTotalCount()
        displayItems = loader.getDisplayItems()
        Log.d("VodGrid", "filter load complete for $kind: displayItems=${displayItems.size}, totalCount=$totalCount")
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
                Log.d("VodGrid", "pagination trigger for $kind: loading page=$nextPage (currentPage=$currentPage, maxPages=$maxPages)")
                isLoadingPage = true
                runCatching {
                    loader.loadPage(
                        nextPage,
                        selectedCountry.takeUnless { it == ALL_OPTION },
                        selectedGroup.takeUnless { it == ALL_OPTION })
                }.onSuccess {
                    val newItems = loader.getDisplayItems()
                    Log.d("VodGrid", "page $nextPage loaded for $kind: cache.size=${newItems.size}")
                    displayItems = newItems
                    currentPage = nextPage
                }.onFailure {
                    loadError = it.message ?: "No se pudo cargar mas contenido"
                    Log.e("VodGrid", "page $nextPage failed for $kind: $loadError")
                }
                isLoadingPage = false
            }
    }

    val displayItemsForGrid = remember(displayItems) { displayItems }
    val itemFocusRequesters = remember(displayItemsForGrid.size) {
        List(displayItemsForGrid.size) { FocusRequester() }
    }

    LaunchedEffect(fragment.contentFocusTrigger) {
        if (fragment.contentFocusTrigger == 0 || displayItemsForGrid.isEmpty()) return@LaunchedEffect
        if (forceFocusFirstItem) return@LaunchedEffect
        Log.d("MainShellFocus", "vod contentFocusTrigger searchQuery='$searchQuery' -> focusing first item kind=$kind")
        runCatching {
            lazyGridState.scrollToItem(0)
            delay(80.milliseconds)
            itemFocusRequesters.firstOrNull()?.requestFocus()
        }.onSuccess {
            Log.d("MainShellFocus", "vod first item requestFocus success kind=$kind items=${displayItemsForGrid.size}")
        }.onFailure {
            Log.w("MainShellFocus", "vod first item requestFocus failed kind=$kind: ${it.message}")
        }
    }

    LaunchedEffect(fragment.searchBackTrigger) {
        if (fragment.searchBackTrigger == 0) return@LaunchedEffect
        Log.d("FocusTrace", "searchBackTrigger fired for $kind -> forceFocus")
        forceFocusFirstItem = true
    }

    LaunchedEffect(forceFocusFirstItem) {
        if (!forceFocusFirstItem) return@LaunchedEffect
        var retries = 3
        while (retries-- > 0 && displayItemsForGrid.isEmpty()) {
            delay(100.milliseconds)
        }
        if (displayItemsForGrid.isEmpty()) return@LaunchedEffect
        Log.d("FocusTrace", "forceFocusFirstItem EXECUTING -> scrollToItem(0) + requestFocus kind=$kind")
        lazyGridState.scrollToItem(0)
        delay(50.milliseconds)
        itemFocusRequesters.firstOrNull()?.requestFocus()
        forceFocusFirstItem = false
        Log.d("FocusTrace", "forceFocusFirstItem DONE kind=$kind")
    }

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
            onSearchImeDismissed = { Log.d("FocusTrace", "onSearchImeDismissed CALLED kind=$kind"); forceFocusFirstItem = true },
            idiomaLabel = "Idioma",
        )
        if (loadError != null && displayItemsForGrid.isEmpty() && !isLoadingPage) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Error al cargar contenido: $loadError",
                    color = IptvTextMuted,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (displayItemsForGrid.isEmpty() && !isLoadingPage) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isNotBlank()) "No hay resultados para \"$searchQuery\"" else "No hay contenido disponible",
                    color = IptvTextMuted,
                    fontSize = 18.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                state = lazyGridState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(displayItemsForGrid, key = { _, item -> item.stableId }) { index, item ->
                    val cwItem = fragment.continueWatchingEntries[item.stableId]
                        ?: fragment.continueWatchingEntries[item.providerId.orEmpty()]
                        ?: item.providerId?.substringAfterLast(":")
                            ?.let { fragment.continueWatchingEntries["movie:$it"]
                                ?: fragment.continueWatchingEntries["series:$it"] }
                        ?: run {
                            val titleKey = when (item.kind) {
                                ContentKind.SERIES -> item.seriesName?.trim()?.lowercase()
                                ContentKind.MOVIE  -> (item.normalizedTitle ?: item.title).trim().lowercase()
                                else -> null
                            }
                            titleKey?.let { fragment.continueWatchingEntries["title:$it"] }
                        }
                    val itemWithWatched =
                        if (item.kind == ContentKind.MOVIE || item.kind == ContentKind.SERIES)
                            item.copy(isWatched = cwItem?.isWatched == true) else item
                    MediaCard(
                        item = itemWithWatched,
                        modifier = Modifier.focusRequester(itemFocusRequesters[index]),
                        narrowCard = true,
                        onFocused = {
                            fragment.contentFocusCanOpenRail = index % gridColumns == 0
                            fragment.selectedHero = item
                        }) {
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
        title = "Selecciona idioma",
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

// ── Discover (Movies + Series unified) ─────────────────────────────────────

@Composable
internal fun DiscoverContent(fragment: ComposeMainFragment) {
    var selectedTab by rememberSaveable { mutableStateOf(ContentKind.MOVIE) }
    val gridColumns = 5
    var selectedCountry by rememberSaveable { mutableStateOf(ALL_OPTION) }
    var selectedGenre by rememberSaveable { mutableStateOf(ALL_OPTION) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showTypeDialog by remember { mutableStateOf(false) }
    var showCountryDialog by remember { mutableStateOf(false) }
    var showGenreDialog by remember { mutableStateOf(false) }
    val lazyGridState = rememberLazyGridState()

    val typeOptions = listOf(
        FilterOptionDto(ContentKind.MOVIE.name, "Peliculas"),
        FilterOptionDto(ContentKind.SERIES.name, "Series"),
        FilterOptionDto(ContentKind.UFC.name, "UFC"),
    )

    val loader = fragment.discoverLoaders.getValue(selectedTab)
    var displayItems by remember { mutableStateOf(loader.getDisplayItems()) }
    var totalCount by remember { mutableIntStateOf(loader.getTotalCount()) }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    var isLoadingPage by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val pageSize = 50

    val currentFilters = when (selectedTab) {
        ContentKind.MOVIE -> fragment.movieFilters
        ContentKind.UFC -> CatalogFilters()
        else -> fragment.seriesFilters
    }
    val countryOptions = remember(currentFilters) {
        buildList {
            add(FilterOptionDto(ALL_OPTION, "Todos"))
            currentFilters.countries.forEach(::add)
        }
    }
    var genreOptions by remember { mutableStateOf<List<FilterOptionDto>>(emptyList()) }
    var forceFocusFirstItem by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab, selectedCountry, currentFilters) {
        val country = selectedCountry.takeUnless { it == ALL_OPTION }
        val filters = if (country != null) {
            runCatching { fragment.repository.loadCatalogFilters(selectedTab, country) }
                .getOrElse { currentFilters }
        } else currentFilters
        genreOptions = buildList {
            add(FilterOptionDto(ALL_OPTION, "Todos"))
            addAll(filters.genres.distinctBy { it.value })
        }
    }

    // Only reset genre when the country/tab filter actually changes, not on restoration.
    var previousFilterKey by remember { mutableStateOf(selectedTab to selectedCountry) }
    LaunchedEffect(selectedTab, selectedCountry) {
        val newKey = selectedTab to selectedCountry
        if (newKey != previousFilterKey) {
            selectedGenre = ALL_OPTION
            previousFilterKey = newKey
        }
    }

    var lastLoadKey by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(selectedTab, selectedCountry, selectedGenre, searchQuery) {
        val key = "$selectedTab|$selectedCountry|$selectedGenre|$searchQuery"
        if (key == lastLoadKey && loader.getDisplayItems().isNotEmpty()) {
            return@LaunchedEffect
        }
        Log.d("DiscoverContent", "filter changed: key=$key, reloading")
        loader.clear(); currentPage = 0; isLoadingPage = false
        if (searchQuery.isNotBlank()) {
            delay(300.milliseconds)
        }
        lastLoadKey = key
        val country = selectedCountry.takeUnless { it == ALL_OPTION }
        val genre = selectedGenre.takeUnless { it == ALL_OPTION }
        loadError = null
        Log.d("DiscoverContent", "loading: country=$country, genre=$genre, search=$searchQuery")
        runCatching {
            if (searchQuery.isNotBlank()) {
                loader.loadSearch(searchQuery, country, genre = genre)
            } else {
                loader.loadPage(0, country, genre = genre)
            }
        }.onFailure {
            Log.e("DiscoverContent", "load failed", it)
            loadError = it.message ?: "No se pudo cargar el contenido"
        }
        totalCount = loader.getTotalCount()
        displayItems = loader.getDisplayItems()
        Log.d("DiscoverContent", "load complete: items=${displayItems.size}, total=$totalCount")
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
                Log.d("DiscoverContent", "pagination: page=$nextPage, current=$currentPage, max=$maxPages")
                isLoadingPage = true
                runCatching {
                    loader.loadPage(
                        nextPage,
                        selectedCountry.takeUnless { it == ALL_OPTION },
                        genre = selectedGenre.takeUnless { it == ALL_OPTION })
                }.onSuccess {
                    val newItems = loader.getDisplayItems()
                    displayItems = newItems
                    currentPage = nextPage
                }.onFailure {
                    Log.e("DiscoverContent", "page $nextPage failed", it)
                    loadError = it.message ?: "No se pudo cargar mas contenido"
                }
                isLoadingPage = false
            }
    }

    val displayItemsForGrid = remember(displayItems) { displayItems }
    val previousRequesters = remember { mutableListOf<FocusRequester>() }
    val itemFocusRequesters = remember(displayItemsForGrid.size) {
        val newList = MutableList(displayItemsForGrid.size) { FocusRequester() }
        for (i in previousRequesters.indices) {
            if (i < newList.size) {
                newList[i] = previousRequesters[i]
            }
        }
        previousRequesters.clear()
        previousRequesters.addAll(newList)
        newList
    }

    LaunchedEffect(fragment.contentFocusTrigger) {
        // discoverFocusLocked stays true throughout the entire restore window
        // to prevent onFocused from corrupting discoverFocusedItemStableId.
        // It is unlocked only at the end of this effect.
        if (fragment.contentFocusTrigger == 0) {
            fragment.discoverFocusLocked = false
            Log.d("MainShellFocus", "discover restore skip: trigger=0, unlock, discoverFocusedItemStableId=${fragment.discoverFocusedItemStableId}")
            return@LaunchedEffect
        }
        if (forceFocusFirstItem) {
            fragment.discoverFocusLocked = false
            Log.d("MainShellFocus", "discover restore skip: forceFocusFirstItem, unlock, discoverFocusedItemStableId=${fragment.discoverFocusedItemStableId}")
            return@LaunchedEffect
        }
        // Give the grid time to lay out before scrolling/requesting focus.
        delay(300.milliseconds)
        // Re-read items from the loader to avoid stale composition captures.
        val items = loader.getDisplayItems()
        if (items.isEmpty()) {
            fragment.discoverFocusLocked = false
            Log.d("MainShellFocus", "discover restore skip: items empty, unlock")
            return@LaunchedEffect
        }
        val focusedStableId = fragment.discoverFocusedItemStableId
        val index = focusedStableId?.let { id ->
            items.indexOfFirst { it.stableId == id }
        } ?: -1
        if (index >= 0) {
            for (attempt in 1..4) {
                try {
                    lazyGridState.scrollToItem(index)
                    delay((100 * attempt).milliseconds)
                    val fr = itemFocusRequesters.getOrNull(index)
                    if (fr != null) {
                        fr.requestFocus()
                        Log.d("MainShellFocus", "discover focus RESTORED: index=$index id=$focusedStableId on attempt $attempt")
                        break
                    }
                } catch (e: Exception) {
                    Log.w("MainShellFocus", "discover focus restore FAILED attempt $attempt: ${e.message}")
                }
            }
        } else {
            runCatching {
                lazyGridState.scrollToItem(0)
                delay(80.milliseconds)
                itemFocusRequesters.firstOrNull()?.requestFocus()
            }
        }
        fragment.discoverFocusLocked = false
        Log.d("MainShellFocus", "discover restore done: unlock, discoverFocusedItemStableId=${fragment.discoverFocusedItemStableId} index=$index")
    }

    LaunchedEffect(fragment.searchBackTrigger) {
        if (fragment.searchBackTrigger == 0) return@LaunchedEffect
        forceFocusFirstItem = true
    }

    LaunchedEffect(forceFocusFirstItem) {
        if (!forceFocusFirstItem) return@LaunchedEffect
        var retries = 3
        while (retries-- > 0 && displayItemsForGrid.isEmpty()) {
            delay(100.milliseconds)
        }
        if (displayItemsForGrid.isEmpty()) return@LaunchedEffect
        lazyGridState.scrollToItem(0)
        delay(50.milliseconds)
        itemFocusRequesters.firstOrNull()?.requestFocus()
        forceFocusFirstItem = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(title = "Discover", subtitle = "")

        val showFilters = selectedTab != ContentKind.UFC
        FilterTopBarDiscover(
            selectedTipo = typeOptions.firstOrNull { it.value == selectedTab.name }?.label ?: "Peliculas",
            selectedIdioma = countryOptions.firstOrNull { it.value == selectedCountry }?.label
                ?: selectedCountry,
            selectedGenero = genreOptions.firstOrNull { it.value == selectedGenre }?.label
                ?: selectedGenre,
            onTipoClicked = { showTypeDialog = true },
            onIdiomaClicked = { showCountryDialog = true },
            onGeneroClicked = { showGenreDialog = true },
            tipoFocusRequester = remember { FocusRequester() },
            idiomaFocusRequester = remember { FocusRequester() },
            generoFocusRequester = remember { FocusRequester() },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            searchFocusRequester = remember { FocusRequester() },
            onSearchImeDismissed = { forceFocusFirstItem = true },
            showIdioma = showFilters,
            showGenero = showFilters,
            showSearch = showFilters,
        )

        if (loadError != null && displayItemsForGrid.isEmpty() && !isLoadingPage) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Error al cargar contenido: $loadError",
                    color = IptvTextMuted,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (displayItemsForGrid.isEmpty() && !isLoadingPage) {
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
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(displayItemsForGrid, key = { _, item -> item.stableId }) { index, item ->
                    val cwItem = fragment.continueWatchingEntries[item.stableId]
                        ?: fragment.continueWatchingEntries[item.providerId.orEmpty()]
                        ?: item.providerId?.substringAfterLast(":")
                            ?.let { fragment.continueWatchingEntries["movie:$it"]
                                ?: fragment.continueWatchingEntries["series:$it"] }
                        ?: run {
                            val titleKey = when (item.kind) {
                                ContentKind.SERIES -> item.seriesName?.trim()?.lowercase()
                                ContentKind.MOVIE  -> (item.normalizedTitle ?: item.title).trim().lowercase()
                                else -> null
                            }
                            titleKey?.let { fragment.continueWatchingEntries["title:$it"] }
                        }
                    val itemWithWatched =
                        if (item.kind == ContentKind.MOVIE || item.kind == ContentKind.SERIES)
                            item.copy(isWatched = cwItem?.isWatched == true) else item
                    if (item.kind == ContentKind.UFC) {
                        UfcCard(
                            item = itemWithWatched,
                            modifier = Modifier.focusRequester(itemFocusRequesters[index]),
                            onFocused = {
                                if (!fragment.discoverFocusLocked) {
                                    fragment.discoverFocusedItemStableId = item.stableId
                                    fragment.contentFocusCanOpenRail = index % gridColumns == 0
                                    fragment.selectedHero = item
                                }
                            },
                            onClick = {
                                fragment.handleCardClick(item, displayItemsForGrid)
                            },
                        )
                    } else {
                        MediaCard(
                            item = itemWithWatched,
                            modifier = Modifier.focusRequester(itemFocusRequesters[index]),
                            narrowCard = true,
                            onFocused = {
                                if (!fragment.discoverFocusLocked) {
                                    fragment.discoverFocusedItemStableId = item.stableId
                                    fragment.contentFocusCanOpenRail = index % gridColumns == 0
                                    fragment.selectedHero = item
                                }
                            }) {
                            fragment.handleCardClick(
                                item,
                                displayItemsForGrid
                            )
                        }
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

    if (showTypeDialog) FilterDialog(
        title = "Selecciona tipo",
        options = typeOptions,
        selectedOption = selectedTab.name,
        onOptionSelected = {
            val newTab = ContentKind.valueOf(it.value)
            if (newTab != selectedTab) {
                selectedTab = newTab
                selectedCountry = ALL_OPTION
                selectedGenre = ALL_OPTION
                searchQuery = ""
                displayItems = emptyList()
                currentPage = 0
                lastLoadKey = ""
                fragment.discoverFocusedItemStableId = null
            }
            showTypeDialog = false
        },
        onDismiss = { showTypeDialog = false })
    if (showCountryDialog) FilterDialog(
        title = "Selecciona idioma",
        options = countryOptions,
        selectedOption = selectedCountry,
        onOptionSelected = { selectedCountry = it.value; showCountryDialog = false },
        onDismiss = { showCountryDialog = false })
    if (showGenreDialog) FilterDialog(
        title = "Selecciona género",
        options = genreOptions,
        selectedOption = selectedGenre,
        onOptionSelected = { selectedGenre = it.value; showGenreDialog = false },
        onDismiss = { showGenreDialog = false })
}

@Composable
private fun FilterTopBarDiscover(
    selectedTipo: String,
    selectedIdioma: String,
    selectedGenero: String,
    onTipoClicked: () -> Unit,
    onIdiomaClicked: () -> Unit,
    onGeneroClicked: () -> Unit,
    tipoFocusRequester: FocusRequester,
    idiomaFocusRequester: FocusRequester,
    generoFocusRequester: FocusRequester,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchFocusRequester: FocusRequester,
    onSearchImeDismissed: () -> Unit = {},
    showIdioma: Boolean = true,
    showGenero: Boolean = true,
    showSearch: Boolean = true,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(label = "Tipo: $selectedTipo", focusRequester = tipoFocusRequester, onClick = onTipoClicked)
        if (showIdioma) {
            FilterChip(label = "Idioma: $selectedIdioma", focusRequester = idiomaFocusRequester, onClick = onIdiomaClicked)
        }
        if (showGenero) {
            FilterChip(label = "Género: $selectedGenero", focusRequester = generoFocusRequester, onClick = onGeneroClicked)
        }
        Spacer(Modifier.weight(1f))
        if (showSearch) {
            SearchBar(query = searchQuery, onQueryChange = onSearchQueryChange, focusRequester = searchFocusRequester, onImeDismissed = onSearchImeDismissed)
        }
    }
}
