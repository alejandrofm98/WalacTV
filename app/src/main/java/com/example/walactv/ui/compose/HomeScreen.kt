package com.example.walactv.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import android.widget.ImageView.ScaleType
import com.example.walactv.BrowseSection
import com.example.walactv.CatalogItem
import com.example.walactv.ComposeMainFragment
import com.example.walactv.ContentKind
import com.example.walactv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// ── Home screen ────────────────────────────────────────────────────────────

@Composable
internal fun HomeContent(fragment: ComposeMainFragment) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { ScreenHeader(title = "Inicio", subtitle = "") }
        items(fragment.homeSections) { section ->
            ContentSection(
                fragment = fragment,
                section = section,
                onFocused = { fragment.selectedHero = it },
                onLoadMore = if (section.contentType != null && section.groupName != null && section.hasNextPage) { sectionToLoad, onDone ->
                    fragment.scope.launch {
                        try {
                            val nextPage = sectionToLoad.currentPage + 1
                            val (newItems, hasNext) = fragment.repository.loadContentPage(
                                sectionToLoad.contentType!!, sectionToLoad.groupName!!, nextPage, 12, sectionToLoad.year
                            )
                            if (newItems.isNotEmpty()) {
                                val updated = sectionToLoad.copy(
                                    items = sectionToLoad.items + newItems, currentPage = nextPage, hasNextPage = hasNext
                                )
                                val index = fragment.homeSections.indexOfFirst { it.title == sectionToLoad.title }
                                if (index >= 0) fragment.homeSections = fragment.homeSections.toMutableList().also { it[index] = updated }
                            }
                        } finally { onDone() }
                    }
                } else null,
            )
        }
    }

    fragment.deleteContinueWatchingItem?.let { item ->
        val isSeries = item.kind == ContentKind.SERIES
        DeleteConfirmationOverlay(
            item = item, isSeries = isSeries,
            onDismiss = { fragment.deleteContinueWatchingItem = null },
            onConfirm = {
                fragment.scope.launch {
                    val contentId = item.providerId.orEmpty().ifBlank { item.stableId.orEmpty().substringAfterLast(":") }
                    if (isSeries) fragment.deleteAllSeriesProgress(item.seriesName ?: item.title)
                    else fragment.watchProgressRepo.deleteProgress(contentId)
                    fragment.deleteContinueWatchingItem = null
                    fragment.loadContinueWatching()
                }
            },
        )
    }
}

// ── Content section ────────────────────────────────────────────────────────

@Composable
internal fun ContentSection(
    fragment: ComposeMainFragment,
    section: BrowseSection,
    onFocused: (CatalogItem) -> Unit,
    onLoadMore: ((BrowseSection, () -> Unit) -> Unit)? = null,
) {
    val lazyListState = rememberLazyListState()
    var isLoadingMore by remember { mutableStateOf(false) }

    LaunchedEffect(section.items) {
        if (section.items.firstOrNull()?.kind == ContentKind.EVENT) {
            val index = fragment.findNextEventIndex(section.items)
            if (index > 0) lazyListState.scrollToItem(index)
        }
    }

    LaunchedEffect(lazyListState, section.hasNextPage, onLoadMore) {
        if (onLoadMore == null || !section.hasNextPage || isLoadingMore) return@LaunchedEffect
        snapshotFlow { lazyListState.layoutInfo }
            .map { info -> (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount }
            .distinctUntilChanged()
            .collect { (lastVisible, totalItems) ->
                if (totalItems > 0 && lastVisible >= totalItems - 5) {
                    isLoadingMore = true
                    onLoadMore(section) { isLoadingMore = false }
                }
            }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(section.title, color = IptvTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            section.contentType?.let { type ->
                if (type == "movies" || type == "series") {
                    Text(
                        text = if (type == "movies") "PELICULAS" else "SERIES",
                        color = if (type == "movies") Color(0xFFE91E63) else Color(0xFF2196F3),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        LazyRow(state = lazyListState, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(section.items) { item ->
                if (section.title == "Continuar viendo") {
                    ContinueWatchingCard(
                        fragment = fragment, item = item,
                        progressPercent = fragment.continueWatchingEntries[item.stableId]?.progressPercent ?: 0,
                        isWatched = fragment.continueWatchingEntries[item.stableId]?.isWatched == true,
                        onFocused = { onFocused(item) },
                        onDeleteRequest = { fragment.deleteContinueWatchingItem = it },
                    )
                } else {
                    val wp = fragment.continueWatchingEntries[item.stableId]
                    val itemWithWatched = if (item.kind == ContentKind.MOVIE || item.kind == ContentKind.SERIES)
                        item.copy(isWatched = wp?.isWatched == true) else item
                    MediaCard(item = itemWithWatched, onFocused = { onFocused(item) }) { fragment.handleCardClick(item, section.items) }
                }
            }
        }
    }
}

// ── Media card ─────────────────────────────────────────────────────────────

@Composable
internal fun MediaCard(item: CatalogItem, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val isChannelOrEvent = item.kind == ContentKind.CHANNEL || item.kind == ContentKind.EVENT
    val cardWidth = if (isChannelOrEvent) 190.dp else 140.dp
    val imageHeight = if (isChannelOrEvent) 107.dp else 200.dp

    Column(
        modifier = Modifier.width(cardWidth)
            .background(if (isFocused) IptvFocusBg else IptvCard, RoundedCornerShape(10.dp))
            .border(if (isFocused) 2.dp else 1.dp, if (isFocused) IptvFocusBorder else IptvSurfaceVariant, RoundedCornerShape(10.dp))
            .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocused() }
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(imageHeight)
                .background(IptvSurfaceVariant, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                item.kind == ContentKind.EVENT -> EventSportPlaceholder(item)
                item.imageUrl.isNotBlank() -> RemoteImage(url = item.imageUrl, width = 300, height = 400,
                    scaleType = if (item.kind == ContentKind.CHANNEL) ScaleType.FIT_CENTER else ScaleType.CENTER_CROP)
                else -> PlaceholderIcon(kind = item.kind)
            }
            item.badgeText.takeIf { it.isNotBlank() && it !in REDUNDANT_BADGES && item.kind != ContentKind.CHANNEL }?.let { badge ->
                Box(modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                    .background(if (item.kind == ContentKind.EVENT) IptvLive else IptvSurface, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(badge, color = IptvTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (item.isWatched) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        val isVod = item.kind == ContentKind.MOVIE || item.kind == ContentKind.SERIES
        Column(
            modifier = Modifier.fillMaxWidth()
                .then(if (isChannelOrEvent) Modifier.height(78.dp) else Modifier.height(64.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = if (isVod) Arrangement.Center else Arrangement.Top,
        ) {
            val displayTitle = item.resolveDisplayTitle()
            Text(displayTitle, color = IptvTextPrimary, fontSize = if (isChannelOrEvent) 15.sp else 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!isVod) {
                Spacer(Modifier.weight(1f))
                val rawSub = item.subtitle
                val displaySub = if (item.badgeText.isNotBlank() && rawSub.contains(item.badgeText)) rawSub.replace(item.badgeText, "").replace("•", "").trim() else rawSub
                Text(displaySub.ifBlank { item.group.ifBlank { kindLabel(item.kind) } }, color = IptvTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ── Continue-watching card ─────────────────────────────────────────────────

@Composable
internal fun ContinueWatchingCard(
    fragment: ComposeMainFragment,
    item: CatalogItem,
    progressPercent: Int = 0,
    isWatched: Boolean = false,
    onFocused: (CatalogItem) -> Unit,
    onDeleteRequest: (CatalogItem) -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val isChannelOrEvent = item.kind == ContentKind.CHANNEL || item.kind == ContentKind.EVENT
    val cardWidth = if (isChannelOrEvent) 190.dp else 140.dp
    val imageHeight = if (isChannelOrEvent) 107.dp else 200.dp
    val scope = rememberCoroutineScope()
    var longPressTriggered by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction: Interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    if (fragment.deleteContinueWatchingItem != null) return@collect
                    longPressTriggered = false
                    longPressJob = scope.launch {
                        delay(800L)
                        longPressTriggered = true
                        onDeleteRequest(item)
                    }
                }
                is PressInteraction.Release -> {
                    longPressJob?.cancel(); longPressJob = null
                    if (fragment.deleteContinueWatchingItem == null && !longPressTriggered) {
                        fragment.handleCardClick(item, listOf(item))
                    }
                    longPressTriggered = false
                }
                is PressInteraction.Cancel -> {
                    longPressJob?.cancel(); longPressJob = null; longPressTriggered = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.width(cardWidth)
            .background(if (isFocused) IptvFocusBg else IptvCard, RoundedCornerShape(10.dp))
            .border(if (isFocused) 2.dp else 1.dp, if (isFocused) IptvFocusBorder else IptvSurfaceVariant, RoundedCornerShape(10.dp))
            .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocused(item) }
            .clickable(interactionSource = interactionSource, indication = null) { },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(imageHeight)
                .background(IptvSurfaceVariant, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.imageUrl.isNotBlank()) RemoteImage(url = item.imageUrl, width = 300, height = 400,
                scaleType = if (item.kind == ContentKind.CHANNEL) ScaleType.FIT_CENTER else ScaleType.CENTER_CROP)
            else PlaceholderIcon(kind = item.kind)
            if (isWatched) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        if (progressPercent in 1..99) {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(IptvSurfaceVariant)) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progressPercent / 100f).background(IptvAccent))
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth()
                .height(if (item.subtitle.isNotBlank()) 78.dp else 56.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(item.resolveDisplayTitle(), color = IptvTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) Text(item.subtitle, color = IptvTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Delete confirmation dialog ─────────────────────────────────────────────

@Composable
internal fun DeleteConfirmationOverlay(
    item: CatalogItem,
    isSeries: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val dialogMessage = if (isSeries)
        "¿Quieres eliminar toda la serie \"${item.seriesName ?: item.title}\" de tu historial de reproducción?"
    else
        "¿Quieres eliminar \"${item.title}\" de tu historial de reproducción?"

    val focusRequester = remember { FocusRequester() }
    var selectedButton by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft  -> { selectedButton = 1; true }
                        Key.DirectionRight -> { selectedButton = 2; true }
                        Key.DirectionCenter,
                        Key.Enter -> {
                            when (selectedButton) { 1 -> onDismiss(); 2 -> onConfirm() }; true
                        }
                        Key.Back,
                        Key.Escape -> { onDismiss(); true }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.width(400.dp).background(Color(0xFF1A1A2E), RoundedCornerShape(16.dp))
                    .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(16.dp)).padding(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Eliminar de Continuar viendo", color = IptvTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(dialogMessage, color = IptvTextMuted, fontSize = 14.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (selectedButton == 1) IptvFocusBg else Color.Transparent, RoundedCornerShape(8.dp))
                            .border(if (selectedButton == 1) 2.dp else 0.dp, if (selectedButton == 1) IptvFocusBorder else Color.Transparent, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp).clickable { onDismiss() }) {
                            Text("Cancelar", color = if (selectedButton == 1) IptvTextPrimary else IptvTextMuted, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (selectedButton == 2) IptvLive.copy(alpha = 0.8f) else IptvLive, RoundedCornerShape(8.dp))
                            .border(if (selectedButton == 2) 2.dp else 0.dp, if (selectedButton == 2) IptvTextPrimary else Color.Transparent, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp).clickable { onConfirm() }) {
                            Text("Eliminar", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ── Shared helpers ─────────────────────────────────────────────────────────

@Composable
internal fun WatchedBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = Icons.Filled.Visibility, contentDescription = "Visto", tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
        Text("VISTO", color = Color(0xFF4CAF50), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun EventSportPlaceholder(item: CatalogItem, emojiSize: TextUnit = 48.sp) {
    val category = item.group.lowercase()
    val text = java.text.Normalizer.normalize(category, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    val emoji = when {
        text.contains("futbol") -> "⚽"; text.contains("baloncesto") -> "🏀"; text.contains("tenis") -> "🎾"
        text.contains("motociclismo") -> "🏍️"; text.contains("automovilismo") -> "🏎️"
        text.contains("mma") || text.contains("boxeo") -> "🥊"; text.contains("rugby") -> "🏈"
        text.contains("balonmano") -> "🤾"; text.contains("hockey") -> "🏒"; text.contains("padel") -> "🏸"
        else -> "🏆"
    }
    val colors = when {
        text.contains("futbol") -> listOf(Color(0xFF0B6E4F), Color(0xFF1A936F))
        text.contains("baloncesto") -> listOf(Color(0xFF7F4F24), Color(0xFFD68C45))
        text.contains("tenis") -> listOf(Color(0xFF254441), Color(0xFF43AA8B))
        text.contains("motociclismo") || text.contains("automovilismo") -> listOf(Color(0xFF1D3557), Color(0xFF457B9D))
        text.contains("mma") || text.contains("boxeo") -> listOf(Color(0xFF5F0F40), Color(0xFF9A031E))
        else -> listOf(Color(0xFF102A43), Color(0xFFD64550))
    }
    Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
        Text(text = emoji, fontSize = emojiSize, textAlign = TextAlign.Center)
    }
}

internal fun CatalogItem.resolveDisplayTitle(): String = when {
    kind == ContentKind.SERIES && !seriesName.isNullOrBlank() -> seriesName
    else -> normalizedTitle?.takeUnless { it.equals("null", ignoreCase = true) }?.takeIf { it.isNotBlank() }
        ?: title.takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
}

private val REDUNDANT_BADGES = setOf("CINE", "SERIE", "Pelicula", "Serie")
