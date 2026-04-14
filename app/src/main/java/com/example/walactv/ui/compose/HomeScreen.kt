package com.example.walactv.ui

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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

// ── Constantes de diseño ───────────────────────────────────────────────────

// Cards VOD (películas / series) — más anchas para mejor legibilidad
internal val VOD_CARD_WIDTH = 160.dp
private val VOD_IMAGE_HEIGHT  = 224.dp   // ratio ~2:3
// Área de texto unificada: siempre la misma altura para VOD (con o sin subtítulo)
// Línea 1: título (puede ocupar hasta 2 líneas)
// Línea 2: subtítulo/episodio (siempre reservado, vacío en películas)
private val VOD_TEXT_AREA_HEIGHT = 72.dp

// Cards canal / evento
private val CH_CARD_WIDTH   = 200.dp
private val CH_IMAGE_HEIGHT = 112.dp
private val CH_TEXT_AREA_HEIGHT = 68.dp

// Custom BringIntoViewSpec for Stremio-style focus scrolling (snap to left edge)
@OptIn(ExperimentalFoundationApi::class)
private val StremioBringIntoViewSpec = object : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = snap()

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailingEdge = offset + size
        return when {
            offset < 0f && trailingEdge > containerSize -> 0f
            else -> offset
        }
    }
}

// ── Home screen ────────────────────────────────────────────────────────────

@Composable
internal fun HomeContent(fragment: ComposeMainFragment) {
    val focusRequesters = remember(fragment.homeSections.size) {
        List(fragment.homeSections.size) { FocusRequester() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { ScreenHeader(title = "Inicio", subtitle = "") }
        itemsIndexed(fragment.homeSections) { index, section ->
            ContentSection(
                fragment = fragment,
                section = section,
                selfFocusRequester = focusRequesters[index],
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

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun ContentSection(
    fragment: ComposeMainFragment,
    section: BrowseSection,
    selfFocusRequester: FocusRequester,
    onFocused: (CatalogItem) -> Unit,
    onLoadMore: ((BrowseSection, () -> Unit) -> Unit)? = null,
) {
    val lazyListState = rememberLazyListState()

    var isLoadingMore by remember { mutableStateOf(false) }

    val focusRequesters = remember(section.items.size) {
        List(section.items.size) { FocusRequester() }
    }

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

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                section.title,
                color = IptvTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            section.contentType?.let { type ->
                if (type == "movies" || type == "series") {
                    Box(
                        modifier = Modifier
                            .background(
                                if (type == "movies") Color(0xFFE91E63).copy(alpha = 0.15f)
                                else Color(0xFF2196F3).copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = if (type == "movies") "PELÍCULAS" else "SERIES",
                            color = if (type == "movies") Color(0xFFE91E63) else Color(0xFF2196F3),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                    }
                }
            }
        }

        CompositionLocalProvider(LocalBringIntoViewSpec provides StremioBringIntoViewSpec) {
            LazyRow(
                state = lazyListState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 32.dp),
                modifier = Modifier.focusProperties {
                    enter = { focusRequesters.getOrNull(lazyListState.firstVisibleItemIndex) ?: selfFocusRequester }
                },
            ) {
                itemsIndexed(section.items) { index, item ->
                    val cardModifier = Modifier
                        .focusRequester(focusRequesters[index])

                    if (section.title == "Continuar viendo") {
                        ContinueWatchingCard(
                            fragment = fragment,
                            item = item,
                            modifier = cardModifier,
                            debugTag = "${section.title}[$index]",
                            progressPercent = fragment.continueWatchingEntries[item.stableId]?.progressPercent ?: 0,
                            isWatched = fragment.continueWatchingEntries[item.stableId]?.isWatched == true,
                            onFocused = { onFocused(item) },
                            onDeleteRequest = { fragment.deleteContinueWatchingItem = it },
                        )
                    } else {
                        val wp = fragment.continueWatchingEntries[item.stableId]
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
                        val itemWithWatched = if (item.kind == ContentKind.MOVIE || item.kind == ContentKind.SERIES)
                            item.copy(isWatched = wp?.isWatched == true) else item
                        MediaCard(
                            item = itemWithWatched,
                            modifier = cardModifier,
                            debugTag = "${section.title}[$index]",
                            onFocused = { onFocused(item) },
                        ) { fragment.handleCardClick(item, section.items) }
                    }
                }
            }
        }
    }
}

// ── Media card ─────────────────────────────────────────────────────────────

@Composable
internal fun MediaCard(
    item: CatalogItem,
    modifier: Modifier = Modifier,
    debugTag: String = "",
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val isChannelOrEvent = item.kind == ContentKind.CHANNEL || item.kind == ContentKind.EVENT
    val cardWidth   = if (isChannelOrEvent) CH_CARD_WIDTH   else VOD_CARD_WIDTH
    val imageHeight = if (isChannelOrEvent) CH_IMAGE_HEIGHT  else VOD_IMAGE_HEIGHT
    val textHeight  = if (isChannelOrEvent) CH_TEXT_AREA_HEIGHT else VOD_TEXT_AREA_HEIGHT

    Column(
        modifier = modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) IptvFocusBg else IptvCard)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color  = if (isFocused) IptvFocusBorder else IptvSurfaceVariant,
                shape  = RoundedCornerShape(10.dp),
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .focusable()
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .background(IptvSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            when {
                item.kind == ContentKind.EVENT -> EventSportPlaceholder(item)
                item.imageUrl.isNotBlank() -> RemoteImage(
                    url = item.imageUrl, width = 300, height = 400,
                    scaleType = if (item.kind == ContentKind.CHANNEL) ScaleType.FIT_CENTER else ScaleType.CENTER_CROP,
                )
                else -> PlaceholderIcon(kind = item.kind)
            }

            item.badgeText.takeIf { it.isNotBlank() && it !in REDUNDANT_BADGES && item.kind != ContentKind.CHANNEL }?.let { badge ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(
                            if (item.kind == ContentKind.EVENT) IptvLive else IptvSurface.copy(alpha = 0.85f),
                            RoundedCornerShape(5.dp),
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(badge, color = IptvTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (item.isWatched) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))

            if (!isChannelOrEvent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                            ),
                        ),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(textHeight)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            val displayTitle = item.resolveDisplayTitle()

            if (item.kind == ContentKind.MOVIE || item.kind == ContentKind.SERIES) {
                Text(
                    text = displayTitle,
                    color = IptvTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                )
            } else {
                Text(
                    text = displayTitle,
                    color = IptvTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.weight(1f))
                val rawSub = item.subtitle
                val displaySub = if (item.badgeText.isNotBlank() && rawSub.contains(item.badgeText))
                    rawSub.replace(item.badgeText, "").replace("•", "").trim()
                else rawSub
                Text(
                    text = displaySub.ifBlank { item.group.ifBlank { kindLabel(item.kind) } },
                    color = IptvTextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Continue-watching card ─────────────────────────────────────────────────

@Composable
internal fun ContinueWatchingCard(
    fragment: ComposeMainFragment,
    item: CatalogItem,
    modifier: Modifier = Modifier,
    debugTag: String = "",
    progressPercent: Int = 0,
    isWatched: Boolean = false,
    onFocused: (CatalogItem) -> Unit,
    onDeleteRequest: (CatalogItem) -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val isChannelOrEvent = item.kind == ContentKind.CHANNEL || item.kind == ContentKind.EVENT
    val cardWidth   = if (isChannelOrEvent) CH_CARD_WIDTH  else VOD_CARD_WIDTH
    val imageHeight = if (isChannelOrEvent) CH_IMAGE_HEIGHT else VOD_IMAGE_HEIGHT
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
        modifier = modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) IptvFocusBg else IptvCard)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color  = if (isFocused) IptvFocusBorder else IptvSurfaceVariant,
                shape  = RoundedCornerShape(10.dp),
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused(item)
                }
            }
            .focusable()
            .clickable(interactionSource = interactionSource, indication = null) { },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(IptvSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (item.imageUrl.isNotBlank()) RemoteImage(
                url = item.imageUrl, width = 300, height = 400,
                scaleType = if (isChannelOrEvent) ScaleType.FIT_CENTER else ScaleType.CENTER_CROP,
            )
            else PlaceholderIcon(kind = item.kind)

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                        ),
                    ),
            )

            if (isWatched) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }

        if (progressPercent in 1..99) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.15f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressPercent / 100f)
                        .background(IptvAccent),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(VOD_TEXT_AREA_HEIGHT)
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.resolveDisplayTitle(),
                    color = IptvTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                )
            }
            Text(
                text = item.subtitle.ifBlank { "" },
                color = IptvAccent.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

    LaunchedEffect(focusRequester) {
        delay(50)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

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
                        Key.Enter -> { when (selectedButton) { 1 -> onDismiss(); 2 -> onConfirm() }; true }
                        Key.Back,
                        Key.Escape -> { onDismiss(); true }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(420.dp)
                    .background(Color(0xFF1A1A2E), RoundedCornerShape(16.dp))
                    .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(16.dp))
                    .padding(28.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Eliminar de Continuar viendo", color = IptvTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(dialogMessage, color = IptvTextMuted, fontSize = 14.sp, lineHeight = 20.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedButton == 1) IptvFocusBg else Color.Transparent)
                                .border(if (selectedButton == 1) 2.dp else 0.dp, if (selectedButton == 1) IptvFocusBorder else Color.Transparent, RoundedCornerShape(8.dp))
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                                .clickable { onDismiss() },
                        ) {
                            Text("Cancelar", color = if (selectedButton == 1) IptvTextPrimary else IptvTextMuted, fontSize = 14.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedButton == 2) IptvLive.copy(alpha = 0.8f) else IptvLive)
                                .border(if (selectedButton == 2) 2.dp else 0.dp, if (selectedButton == 2) IptvTextPrimary else Color.Transparent, RoundedCornerShape(8.dp))
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                                .clickable { onConfirm() },
                        ) {
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
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Visibility,
            contentDescription = "Visto",
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(13.dp),
        )
        Text("VISTO", color = Color(0xFF4CAF50), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
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
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = emojiSize, textAlign = TextAlign.Center)
    }
}

internal fun CatalogItem.resolveDisplayTitle(): String = when {
    kind == ContentKind.SERIES && !seriesName.isNullOrBlank() -> seriesName
    else -> normalizedTitle?.takeUnless { it.equals("null", ignoreCase = true) }?.takeIf { it.isNotBlank() }
        ?: title.takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
}

private val REDUNDANT_BADGES = setOf("CINE", "SERIE", "Pelicula", "Serie")