package com.example.walactv.ui.compose

import android.util.Log
import android.widget.ImageView.ScaleType.CENTER_CROP
import android.widget.ImageView.ScaleType.FIT_CENTER
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.example.walactv.BuildConfig
import com.example.walactv.BrowseSection
import com.example.walactv.CatalogItem
import com.example.walactv.ComposeMainFragment
import com.example.walactv.ContentKind
import com.example.walactv.isVodContent
import com.example.walactv.preferredCardImageUrl
import com.example.walactv.preferredVodPosterUrl
import com.example.walactv.tmdbDebug
import com.example.walactv.ui.theme.IptvAccent
import com.example.walactv.ui.theme.IptvBackground
import com.example.walactv.ui.theme.IptvCard
import com.example.walactv.ui.theme.IptvFocusBg
import com.example.walactv.ui.theme.IptvFocusBorder
import com.example.walactv.ui.theme.IptvLive
import com.example.walactv.ui.theme.IptvSurface
import com.example.walactv.ui.theme.IptvSurfaceVariant
import com.example.walactv.ui.theme.IptvTextAccent
import com.example.walactv.ui.theme.IptvTextMuted
import com.example.walactv.ui.theme.IptvTextPrimary
import com.example.walactv.ui.theme.IptvTextSecondary
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// ── Constantes de diseño ───────────────────────────────────────────────────

// Cards VOD — solo imagen, sin texto debajo (el título está en el hero)
internal val VOD_CARD_WIDTH        = 120.dp
private val VOD_IMAGE_HEIGHT       = 180.dp   // ratio 2:3 exact
private val VOD_TEXT_AREA_HEIGHT = 0.dp     // sin texto para VOD

// Hero inmersivo — ocupa ~55% de la pantalla (el backdrop es fillMaxSize)
private val HOME_HERO_FRACTION    = 0.56f

// Cards canal / evento — mantienen su texto
private val CH_CARD_WIDTH       = 180.dp
private val CH_IMAGE_HEIGHT     = 100.dp
private val CH_TEXT_AREA_HEIGHT = 60.dp

// Cards evento — texto integrado sobre imagen, estilo evento deportivo
private val EVENT_CARD_WIDTH       = 240.dp
private val EVENT_IMAGE_HEIGHT     = 150.dp
private val EVENT_TEXT_AREA_HEIGHT = 52.dp

// Anchos del rail lateral (deben coincidir con SideRail.kt)
private val SIDE_RAIL_COLLAPSED_WIDTH = 78.dp
private val SIDE_RAIL_EXPANDED_WIDTH  = 248.dp

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

    LaunchedEffect(fragment.homeSections) {
        if (fragment.homeSections.isEmpty()) return@LaunchedEffect
        Log.d("HomeContent", "=== SECTIONS ORDER (${fragment.homeSections.size}) ===")
        fragment.homeSections.forEachIndexed { i, s ->
            Log.d("HomeContent", "  [$i] '${s.title}' items=${s.items.size}")
        }
        if (fragment.selectedHero != null || fragment.pendingFocusItem != null) return@LaunchedEffect
        delay(200.milliseconds)
        runCatching { focusRequesters.firstOrNull()?.requestFocus() }
    }

    LaunchedEffect(fragment.contentFocusTrigger) {
        if (fragment.contentFocusTrigger == 0) return@LaunchedEffect
        delay(300.milliseconds)
        if (fragment.pendingFocusItem != null) {
            fragment.pendingFocusTrigger++
        }
    }

    val homePilotEvent = remember(fragment.homeSections) {
        fragment.homeSections.asSequence()
            .map { section -> section.items.filter { it.kind == ContentKind.EVENT } }
            .firstOrNull { it.isNotEmpty() }
            ?.let { events ->
                val nextIndex = fragment.findNextEventIndex(events)
                events.getOrNull(nextIndex.takeIf { it >= 0 } ?: 0)
            }
    }

    val heroItem = remember(fragment.selectedHero, fragment.homeSections, homePilotEvent) {
        fragment.selectedHero?.takeIf { it.isHeroContent() }
            ?: fragment.homeSections.asSequence()
                .flatMap { it.items.asSequence() }
                .firstOrNull { it.isVodContent() }
    }
    val usePilotEventBackdrop = heroItem?.stableId == homePilotEvent?.stableId && heroItem?.imageUrl.orEmpty().isBlank()

    LaunchedEffect(heroItem?.stableId, heroItem?.backdropUrl, heroItem?.description, heroItem?.overviewEn) {
        Log.d("TMDB_HOME", "hero=${heroItem.tmdbDebug()}")
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(IptvBackground)) {
        val screenHeight = maxHeight
        val heroHeight   = screenHeight * HOME_HERO_FRACTION

        // ── FIX: el backdrop ajusta su padding izquierdo dinámicamente
        // según si el rail está expandido o colapsado, eliminando la franja visible.
        // Se elimina el Box tapador que antes usaba 110.dp fijo (insuficiente).
        HomeBackdrop(
            item = heroItem,
            usePilotEventImage = usePilotEventBackdrop,
            isRailExpanded = fragment.isRailExpanded,
            modifier = Modifier.fillMaxSize(),
        )

        Column(modifier = Modifier.fillMaxSize()) {

            HomeHeroText(
                item = heroItem,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                fragment.homeSections.forEachIndexed { index, section ->
                    ContentSection(
                        fragment = fragment,
                        section = section,
                        sectionIndex = index,
                        selfFocusRequester = focusRequesters[index],
                        onFocused = {
                            if (it.isHeroContent()) {
                                Log.d("TMDB_HOME", "focus item=${it.tmdbDebug()}")
                                fragment.selectedHero = it
                            }
                        },
                        onLoadMore = if (section.contentType != null && (section.groupName != null || section.sectionTitle != null || section.year != null) && section.hasNextPage) {
                            { sectionToLoad: BrowseSection, onDone: () -> Unit ->
                                fragment.scope.launch {
                                    try {
                                        val pageSize = 24
                                        val nextPage = sectionToLoad.currentPage + 1
                                        val (newItems, hasNext) = fragment.repository.loadContentPage(
                                            sectionToLoad.contentType!!, sectionToLoad.groupName, nextPage, pageSize, sectionToLoad.year, sectionToLoad.sectionTitle
                                        )
                                        val actuallyHasNext = if (newItems.isEmpty()) false else hasNext
                                        val updated = sectionToLoad.copy(
                                            items = (sectionToLoad.items + newItems).distinctBy(CatalogItem::stableId),
                                            currentPage = nextPage,
                                            hasNextPage = actuallyHasNext
                                        )
                                        val idx = fragment.homeSections.indexOfFirst {
                                            it.title == sectionToLoad.title && it.contentType == sectionToLoad.contentType
                                        }
                                        if (idx >= 0) fragment.homeSections = fragment.homeSections.toMutableList().also { it[idx] = updated }
                                    } finally { onDone() }
                                }
                            }
                        } else null,
                    )
                }
            }
        }
    }

    fragment.deleteContinueWatchingItem?.let { item ->
        val isSeries = item.kind == ContentKind.SERIES
        DeleteConfirmationOverlay(
            item = item, isSeries = isSeries,
            onDismiss = { fragment.deleteContinueWatchingItem = null },
            onConfirm = {
                val contentId = item.providerId.orEmpty().ifBlank { item.stableId.orEmpty().substringAfterLast(":") }
                if (isSeries) fragment.removeContinueWatchingLocally(seriesName = item.seriesName ?: item.title)
                else fragment.removeContinueWatchingLocally(contentId = contentId)
                fragment.deleteContinueWatchingItem = null
                fragment.scope.launch {
                    if (isSeries) fragment.deleteAllSeriesProgress(item.seriesName ?: item.title)
                    else fragment.watchProgressRepo.deleteProgress(contentId)
                    fragment.loadContinueWatching()
                }
            },
        )
    }
}

// ── Backdrop inmersivo ─────────────────────────────────────────────────────

@Composable
private fun HomeBackdrop(
    item: CatalogItem?,
    usePilotEventImage: Boolean,
    isRailExpanded: Boolean,          // ← NUEVO: controla el padding dinámico
    modifier: Modifier = Modifier,
) {
    // El padding izquierdo se anima con la misma duración y easing que el rail lateral,
    // evitando la franja visible durante la transición de apertura/cierre.
    val backdropStartPadding by animateDpAsState(
        targetValue = if (isRailExpanded) SIDE_RAIL_EXPANDED_WIDTH else SIDE_RAIL_COLLAPSED_WIDTH,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "backdropStartPaddingAnim",
    )

    Box(modifier = modifier.background(IptvBackground)) {
        val eventImageUrl = item?.takeIf { it.kind == ContentKind.EVENT }?.imageUrl?.takeIf { it.isNotBlank() }
        val backdropUrl = item?.backdropUrl?.takeIf { it.isNotBlank() }
        val posterUrl   = item?.preferredVodPosterUrl().orEmpty()

        when {
            !eventImageUrl.isNullOrBlank() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = backdropStartPadding),
            ) {
                RemoteImage(
                    url = eventImageUrl,
                    width = 1920,
                    height = 1080,
                    scaleType = CENTER_CROP,
                    disableCache = true,
                )
            }
            usePilotEventImage -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = backdropStartPadding),   // ← dinámico
            ) {
                item?.let { EventSportPlaceholder(it) }
            }
            !backdropUrl.isNullOrBlank() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = backdropStartPadding),   // ← dinámico
            ) {
                RemoteImage(
                    url = backdropUrl,
                    width = 1920,
                    height = 1080,
                    scaleType = CENTER_CROP,
                )
            }
            posterUrl.isNotBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = backdropStartPadding),
                ) {
                    RemoteImage(
                        url = posterUrl,
                        width = 600,
                        height = 900,
                        scaleType = CENTER_CROP,
                    )
                }
            }
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(IptvBackground),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f  to IptvBackground,
                            0.18f to IptvBackground.copy(alpha = 0.94f),
                            0.42f to IptvBackground.copy(alpha = 0.70f),
                            0.68f to IptvBackground.copy(alpha = 0.24f),
                            1.0f  to Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF0A1A3A).copy(alpha = 0.20f),
                            1.0f  to Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f  to Color(0xFF06142F).copy(alpha = 0.22f),
                            0.36f to Color.Transparent,
                            0.58f to IptvBackground.copy(alpha = 0.34f),
                            0.74f to IptvBackground.copy(alpha = 0.86f),
                            1.0f  to IptvBackground,
                        ),
                    ),
                ),
        )
    }
}

// ── Texto hero ─────────────────────────────────────────────────────────────

@Composable
private fun HomeHeroText(item: CatalogItem?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val titleTopPadding = (maxHeight - 250.dp).coerceAtLeast(44.dp)
        AnimatedContent(
            targetState = item,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier
                .padding(start = 44.dp, end = 44.dp, top = titleTopPadding)
                .fillMaxWidth(0.48f),
            label = "heroContent",
        ) { animatedItem ->
            val eventCompetitionText = animatedItem?.takeIf { it.kind == ContentKind.EVENT }?.eventCompetitionText().orEmpty()
            val eventTimeText = animatedItem?.takeIf { it.kind == ContentKind.EVENT }?.badgeText.orEmpty()
                .takeIf { it.isNotBlank() }
            val descriptionText = when {
                animatedItem?.kind == ContentKind.EVENT -> animatedItem.description.takeIf { it.isNotBlank() && it != animatedItem.group }
                else -> animatedItem?.description?.takeIf { it.isNotBlank() && it != animatedItem.group } ?: animatedItem?.overviewEn
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = animatedItem?.resolveDisplayTitle().orEmpty().ifBlank { "Inicio" },
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 48.sp,
                )

                if (animatedItem?.kind == ContentKind.EVENT) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (eventCompetitionText.isNotBlank()) {
                            Text(
                                text = eventCompetitionText,
                                color = IptvTextAccent,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!eventTimeText.isNullOrBlank()) {
                            Text(
                                text = "|",
                                color = Color.White.copy(alpha = 0.30f),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = eventTimeText,
                                color = IptvTextSecondary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    HomeHeroMeta(animatedItem)
                }

                descriptionText?.takeIf { it.isNotBlank() }?.let { text ->
                    Text(
                        text = text,
                        color = IptvTextSecondary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        maxLines = if (animatedItem?.kind == ContentKind.EVENT) 2 else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeroMeta(item: CatalogItem?) {
    val genres = item?.genres.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item?.voteAverage?.takeIf { it > 0f }?.let { rating ->
                Row(
                    modifier = Modifier
                        .background(Color(0xFFE5B35B), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("★", color = Color(0xFF101014), fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", rating),
                        color = Color(0xFF101014),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            item?.year?.let { HomeHeroMetaText(it.toString()) }

            item?.runtimeMinutes?.takeIf { it > 0 }?.let { mins ->
                val h = mins / 60
                val m = mins % 60
                val runtimeStr = if (h > 0) "${h}h ${m}min" else "${m}min"
                HomeHeroMetaText(runtimeStr)
            }

            if (item?.kind == ContentKind.SERIES) {
                item.totalSeasons?.takeIf { it > 0 }?.let { total ->
                    HomeHeroMetaText(if (total == 1) "1 temporada" else "$total temporadas")
                }
            }

            if (item == null) HomeHeroMetaText("TMDB")
        }

        if (genres.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                genres.forEach { genre ->
                    Box(
                        modifier = Modifier
                            .background(IptvAccent.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = genre,
                            color = IptvAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeroMetaText(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.72f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ── Content section ────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun ContentSection(
    fragment: ComposeMainFragment,
    section: BrowseSection,
    sectionIndex: Int,
    selfFocusRequester: FocusRequester,
    onFocused: (CatalogItem) -> Unit,
    onLoadMore: ((BrowseSection, () -> Unit) -> Unit)? = null,
) {
    val lazyListState = rememberLazyListState()
    var isLoadingMore by remember { mutableStateOf(false) }
    var rowWidth by remember { mutableIntStateOf(0) }
    val focusRequesters = remember(section.items.size) {
        List(section.items.size) { FocusRequester() }
    }
    Log.d("HomeContent", "ContentSection[$sectionIndex] '${section.title}' items=${section.items.size} kinds=${section.items.map { it.kind }.distinct()}")
    // Memoize the continue-watching lookup to avoid recomputing per card
    val cwLookup = remember(section.items, fragment.continueWatchingEntries) {
        section.items.associateWith { item ->
            fragment.continueWatchingEntries[item.stableId]
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
        }
    }
    LaunchedEffect(section.items) {
        if (fragment.suppressEventAutoScroll) {
            delay(600.milliseconds)
            if (!fragment.suppressEventAutoScroll) return@LaunchedEffect
        }
        if (section.items.firstOrNull()?.kind == ContentKind.EVENT) {
            val index = fragment.findNextEventIndex(section.items)
            if (index >= 0) {
                lazyListState.scrollToItem(index)
                delay(100.milliseconds)
                focusRequesters.getOrNull(index)?.requestFocus()
            }
        }
    }

    LaunchedEffect(fragment.pendingFocusItem, fragment.pendingFocusTrigger) {
        val target = fragment.pendingFocusItem ?: return@LaunchedEffect
        val targetId = target.stableId
        Log.d("HomeContent", "=== LaunchedEffect FOCUS RESTORE: section=${section.title} targetId=$targetId items=${section.items.size} ===")

        for (attempt in 1..3) {
            val idx = section.items.indexOfFirst { it.stableId == targetId }
            Log.d("HomeContent", "Focus restore attempt $attempt: idx=$idx for targetId=$targetId in section '${section.title}'")
            if (idx >= 0) {
                try {
                    lazyListState.scrollToItem(idx)
                    delay((80 * attempt).milliseconds)
                    val fr = focusRequesters.getOrNull(idx)
                    if (fr != null) {
                        fr.requestFocus()
                        Log.d("HomeContent", "Focus RESTORED: ${section.title}[$idx] on attempt $attempt")
                        fragment.pendingFocusItem = null
                        fragment.suppressEventAutoScroll = false
                        break
                    } else {
                        Log.w("HomeContent", "FocusRequester[$idx] is null, retry...")
                    }
                } catch (e: Exception) {
                    Log.w("HomeContent", "Focus restore FAILED attempt $attempt: ${e.message}")
                }
            } else {
                Log.d("HomeContent", "Item $targetId NOT FOUND in section '${section.title}'")
                if (sectionIndex == 0 && section.items.isNotEmpty() && focusRequesters.isNotEmpty()) {
                    Log.d("HomeContent", "First section fallback: focusing first card")
                    runCatching {
                        lazyListState.scrollToItem(0)
                        delay(80.milliseconds)
                        focusRequesters.first().requestFocus()
                        fragment.pendingFocusItem = null
                        fragment.suppressEventAutoScroll = false
                    }
                }
                break
            }
        }
    }

    LaunchedEffect(fragment.homeFocusRestoreTrigger, section.items) {
        val target = fragment.pendingHomeFocusTarget
        if (target == null) {
            if (sectionIndex != 0) return@LaunchedEffect
            val initialIndex = if (section.items.firstOrNull()?.kind == ContentKind.EVENT) {
                fragment.findNextEventIndex(section.items).takeIf { it >= 0 } ?: 0
            } else 0
            Log.d("HomeContent", "Rail restore has no target, focusing initialIndex=$initialIndex section=${section.title} items=${section.items.size} requesters=${focusRequesters.size}")
            runCatching {
                lazyListState.scrollToItem(initialIndex)
                delay(80.milliseconds)
                val requester = focusRequesters.getOrNull(initialIndex)
                if (requester == null) {
                    Log.w("HomeContent", "Home initial focus skipped: no requester for index $initialIndex section=${section.title}")
                } else {
                    requester.requestFocus()
                    Log.d("HomeContent", "Home initial focus success index=$initialIndex section=${section.title}")
                }
            }.onFailure {
                Log.w("HomeContent", "Home initial focus failed: ${it.message}")
            }
            return@LaunchedEffect
        }
        if (target.sectionTitle != section.title) return@LaunchedEffect
        Log.d("HomeContent", "Rail restore target section=${section.title} itemIndex=${target.itemIndex}")

        val targetIndex = target.itemIndex
            .takeIf { index -> section.items.getOrNull(index)?.stableId == target.itemStableId }
            ?: section.items.indexOfFirst { it.stableId == target.itemStableId }
        if (targetIndex < 0) return@LaunchedEffect

        runCatching {
            lazyListState.scrollToItem(targetIndex)
            delay(80.milliseconds)
            focusRequesters.getOrNull(targetIndex)?.requestFocus()
            fragment.pendingHomeFocusTarget = null
        }.onFailure {
            Log.w("HomeContent", "Home focus restore failed: ${it.message}")
        }
    }

    val stableOnLoadMore = remember(onLoadMore) { onLoadMore }

    LaunchedEffect(lazyListState, section.hasNextPage, section.currentPage) {
        if (stableOnLoadMore == null || !section.hasNextPage || isLoadingMore) return@LaunchedEffect
        snapshotFlow { lazyListState.layoutInfo }
            .map { info -> (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount }
            .distinctUntilChanged()
            .collect { (lastVisible, totalItems) ->
                if (totalItems > 0 && lastVisible >= totalItems - 5 && !isLoadingMore) {
                    isLoadingMore = true
                    stableOnLoadMore?.let { it(section) { isLoadingMore = false } }
                }
            }
    }

    val isEventSection = section.items.firstOrNull()?.kind == ContentKind.EVENT
    val columnModifier = Modifier
        .focusRequester(selfFocusRequester)
        .padding(horizontal = 32.dp)
        .padding(top = if (isEventSection) 8.dp else 16.dp, bottom = if (isEventSection) 6.dp else 16.dp)
        .onFocusChanged { state ->
            if (BuildConfig.DEBUG) Log.d("FOCUS", "ContentSection '${section.title}': onFocusChanged isFocused=${state.isFocused} hasFocus=${state.hasFocus}")
        }

    Column(
        modifier = columnModifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Título de sección ─────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (isEventSection && section.title == "Eventos de hoy") "Eventos" else section.title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            section.contentType?.let { type ->
                if (type == "movies" || type == "series") {
                    Box(
                        modifier = Modifier
                            .background(
                                if (type == "movies") Color(0xFFE91E63).copy(alpha = 0.18f)
                                else Color(0xFF42A5F5).copy(alpha = 0.18f),
                                RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (type == "movies") Color(0xFFE91E63) else Color(0xFF42A5F5),
                                        RoundedCornerShape(999.dp),
                                    ),
                            )
                            Text(
                                text = if (type == "movies") "PELÍCULAS" else "SERIES",
                                color = if (type == "movies") Color(0xFFE91E63) else Color(0xFF42A5F5),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            )
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(LocalBringIntoViewSpec provides StremioBringIntoViewSpec) {
            val density = LocalDensity.current
            val rowPaddingEnd = with(density) { rowWidth.toDp() }.coerceAtLeast(32.dp)
            LazyRow(
                state = lazyListState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = rowPaddingEnd),
                modifier = Modifier
                    .onSizeChanged { rowWidth = it.width }
                    .onFocusChanged { state ->
                        if (BuildConfig.DEBUG) Log.d("FOCUS", "LazyRow '${section.title}': onFocusChanged isFocused=${state.isFocused} hasFocus=${state.hasFocus}")
                    },
            ) {
                itemsIndexed(section.items, key = { _, item -> item.stableId }) { index, item ->
                    val cardModifier = Modifier.focusRequester(focusRequesters[index])

                    if (section.title == "Continuar viendo") {
                        val wp = cwLookup[item]
                        val remainingText = wp?.let { formatDurationRemaining(it.positionMs, it.durationMs) }
                        val epBadge = item.subtitle.ifBlank { null }
                        ContinueWatchingCard(
                            fragment = fragment,
                            item = item,
                            modifier = cardModifier,
                            debugTag = "${section.title}[$index]",
                            progressPercent = wp?.progressPercent ?: 0,
                            isWatched = wp?.isWatched == true,
                            timeRemainingText = remainingText,
                            episodeBadge = epBadge,
                            onFocused = { focusedItem ->
                                fragment.rememberHomeFocus(sectionIndex, section.title, focusedItem, index)
                                onFocused(focusedItem)
                            },
                            onDeleteRequest = { fragment.deleteContinueWatchingItem = it },
                        )
                    } else {
                        val wp = cwLookup[item]
                        val itemWithWatched = if (item.kind == ContentKind.MOVIE || item.kind == ContentKind.SERIES)
                            item.copy(isWatched = wp?.isWatched == true) else item

                        // ── Eventos usan el nuevo EventVsCard ─────────────
                        if (item.kind == ContentKind.EVENT) {
                            val isLive = item.badgeText.matches(Regex("\\d{1,2}:\\d{2}.*")) ||
                                    item.badgeText.contains("LIVE", ignoreCase = true) ||
                                    item.badgeText.contains("EN VIVO", ignoreCase = true)
                            EventVsCard(
                                item = item,
                                modifier = cardModifier,
                                isLive = isLive,
                                channelLineup = fragment.channelLineup,
                                onFocused = {
                                    fragment.rememberHomeFocus(sectionIndex, section.title, item, index)
                                    onFocused(item)
                                },
                                onClick = { fragment.handleCardClick(item, section.items) },
                            )
                        } else {
                            MediaCard(
                                item = itemWithWatched,
                                modifier = cardModifier,
                                debugTag = "${section.title}[$index]",
                                onFocused = {
                                    fragment.rememberHomeFocus(sectionIndex, section.title, item, index)
                                    onFocused(item)
                                },
                            ) { fragment.handleCardClick(item, section.items) }
                        }
                    }
                }
            }
        }
    }
}

// ── Event VS Card ──────────────────────────────────────────────────────────

@Composable
internal fun EventVsCard(
    item: CatalogItem,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
    useFixedWidth: Boolean = true,
    channelLineup: List<CatalogItem> = emptyList(),
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    val displaySubtitle = remember(item.subtitle, item.badgeText, item.group) {
        val cleaned = if (item.badgeText.isNotBlank() && item.subtitle.contains(item.badgeText)) {
            item.subtitle.replace(item.badgeText, "").replace("•", "").trim()
        } else {
            item.subtitle
        }
        val parts = cleaned.split("  •  ", " • ", "•")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0]} | ${parts[1]}"
            parts.size == 1 -> parts[0]
            else -> item.group.takeIf { it.isNotBlank() && it != "Agenda" }.orEmpty()
        }
    }
    val displayTitle = remember(item.title, item.normalizedTitle, item.tmdbTitle, item.seriesName) {
        item.resolveDisplayTitle().replace("\n", " ").replace(Regex("\\s{2,}"), " ")
    }

    val baseModifier = if (useFixedWidth) {
        modifier.width(EVENT_CARD_WIDTH).height(EVENT_IMAGE_HEIGHT)
    } else {
        modifier.fillMaxWidth().aspectRatio(EVENT_CARD_WIDTH / EVENT_IMAGE_HEIGHT)
    }

    Box(
        modifier = baseModifier
            .clip(RoundedCornerShape(16.dp))
            .background(IptvCard)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) IptvFocusBorder else IptvAccent.copy(alpha = 0.26f),
                shape = RoundedCornerShape(16.dp),
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable { onClick() },
    ) {
        val displayImageUrl = remember(item.imageUrl, item.description, channelLineup) {
            if (item.imageUrl.isNotBlank()) return@remember item.imageUrl
            val channelNames = item.description.split("|").map { it.trim() }.filter { it.isNotBlank() }
            channelNames.firstNotNullOfOrNull { name ->
                channelLineup.firstOrNull { channel ->
                    channel.title.contains(name, ignoreCase = true) ||
                    channel.group.contains(name, ignoreCase = true)
                }?.preferredCardImageUrl()?.takeIf { it.isNotBlank() }
            }.orEmpty()
        }
        if (displayImageUrl.isNotBlank()) {
            RemoteImage(
                url = displayImageUrl,
                width = 480,
                height = 300,
                scaleType = CENTER_CROP,
                disableCache = true,
            )
        } else {
            EventSportPlaceholder(item)
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, IptvBackground.copy(alpha = 0.95f)),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF113B87).copy(alpha = 0.28f), Color.Transparent),
                    ),
                ),
        )

        val badge = item.badgeText.takeIf { it.isNotBlank() }
        if (badge != null || isLive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(
                        IptvAccent,
                        RoundedCornerShape(7.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = badge ?: "EN VIVO",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.2.sp,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 14.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = displayTitle,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                modifier = if (displaySubtitle.isBlank()) Modifier.padding(end = 56.dp) else Modifier
            )
            if (displaySubtitle.isNotBlank()) {
                Text(
                    text = displaySubtitle,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 56.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 10.dp)
                .background(IptvBackground.copy(alpha = 0.70f), RoundedCornerShape(999.dp))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("▶", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text("Ver", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
    narrowCard: Boolean = false,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val isVod = item.kind == ContentKind.MOVIE || item.kind == ContentKind.SERIES
    val isChannel = item.kind == ContentKind.CHANNEL
    val isEvent = item.kind == ContentKind.EVENT
    val cardWidth = when {
        isEvent -> EVENT_CARD_WIDTH
        isChannel -> CH_CARD_WIDTH
        else -> VOD_CARD_WIDTH
    }
    val imageHeight = when {
        isEvent -> EVENT_IMAGE_HEIGHT
        isChannel -> CH_IMAGE_HEIGHT
        else -> VOD_IMAGE_HEIGHT
    }
    val textAreaHeight = if (isEvent) EVENT_TEXT_AREA_HEIGHT else CH_TEXT_AREA_HEIGHT

    val vodModifier = if (isVod && narrowCard) {
        Modifier.fillMaxWidth().aspectRatio(2f / 3f)
    } else {
        Modifier.width(cardWidth)
    }

    val baseModifier = modifier
        .then(vodModifier)
        .clip(RoundedCornerShape(10.dp))
        .border(
            width = if (isFocused) 2.dp else if (isVod && narrowCard) 0.dp else 1.dp,
            color  = if (isFocused) IptvFocusBorder else IptvSurfaceVariant,
            shape  = RoundedCornerShape(10.dp),
        )
        .onFocusChanged {
            isFocused = it.isFocused
            if (it.isFocused) onFocused()
        }
        .tvClickable { onClick() }

    if (isVod) {
        Box(
            modifier = baseModifier.then(if (narrowCard) Modifier else Modifier.background(IptvSurfaceVariant)),
            contentAlignment = Alignment.Center,
        ) {
            val cardImgUrl = item.preferredCardImageUrl()
            Log.d("TMDB_IMG", "MediaCard vod stableId=${item.stableId.take(40)} kind=${item.kind} url=${cardImgUrl.take(120)}")
            if (cardImgUrl.isNotBlank()) {
                RemoteImage(
                    url = cardImgUrl,
                    width = 300,
                    height = 450,
                    scaleType = CENTER_CROP,
                )
            } else {
                PlaceholderIcon(kind = item.kind)
            }

            item.badgeText.takeIf { it.isNotBlank() && it !in REDUNDANT_BADGES }?.let { badge ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(IptvSurface.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(badge, color = IptvTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (item.isWatched) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))

            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, IptvFocusBorder, RoundedCornerShape(10.dp)),
                )
            }
        }
    } else {
        Column(
            modifier = baseModifier.background(if (isFocused) IptvFocusBg else IptvCard),
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
                    item.preferredCardImageUrl().isNotBlank() -> RemoteImage(
                        url = item.preferredCardImageUrl(), width = 300, height = 200,
                        scaleType = FIT_CENTER,
                    )
                    else -> PlaceholderIcon(kind = item.kind)
                }

                item.badgeText.takeIf { it.isNotBlank() && it !in REDUNDANT_BADGES }?.let { badge ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                if (item.kind == ContentKind.EVENT) IptvLive else IptvSurface.copy(alpha = 0.85f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(badge, color = IptvTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(textAreaHeight)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.resolveDisplayTitle(),
                    color = IptvTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                )
                val rawSub = item.subtitle
                val displaySub = if (item.badgeText.isNotBlank() && rawSub.contains(item.badgeText))
                    rawSub.replace(item.badgeText, "").replace("•", "").trim()
                else rawSub
                if (displaySub.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = displaySub,
                        color = IptvTextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
    timeRemainingText: String? = null,
    episodeBadge: String? = null,
    onFocused: (CatalogItem) -> Unit,
    onDeleteRequest: (CatalogItem) -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val isChannelOrEvent = item.kind == ContentKind.CHANNEL || item.kind == ContentKind.EVENT
    val cardWidth   = if (isChannelOrEvent) CH_CARD_WIDTH  else VOD_CARD_WIDTH
    val imageHeight = if (isChannelOrEvent) CH_IMAGE_HEIGHT else VOD_IMAGE_HEIGHT
    var keyDownMillis by remember { mutableLongStateOf(0L) }
    var consumeClick by remember { mutableStateOf(false) }

    Box(
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
                if (it.isFocused) onFocused(item)
            }
            .clickable { if (!consumeClick) fragment.handleCardClick(item, listOf(item)) }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter) &&
                    !consumeClick
                ) {
                    fragment.handleCardClick(item, listOf(item))
                    true
                } else false
            }
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            keyDownMillis = event.nativeKeyEvent.downTime
                            consumeClick = false
                            false
                        }
                        KeyEventType.KeyUp -> {
                            val elapsed = event.nativeKeyEvent.eventTime - keyDownMillis
                            if (elapsed >= 800L) {
                                consumeClick = true
                                onDeleteRequest(item)
                                true
                            } else {
                                consumeClick = false
                                false
                            }
                        }
                        else -> false
                    }
                } else false
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(IptvSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val imageUrl = item.preferredCardImageUrl()
            if (imageUrl.isNotBlank()) RemoteImage(
                url = imageUrl, width = 300, height = 450,
                scaleType = if (isChannelOrEvent) FIT_CENTER else CENTER_CROP,
            )
            else PlaceholderIcon(kind = item.kind)

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        ),
                    ),
            )

            if (isWatched) {
                WatchedBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (timeRemainingText != null) {
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = timeRemainingText,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                    if (episodeBadge != null) {
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = episodeBadge,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            if (progressPercent in 1..99) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressPercent / 100f)
                            .background(IptvAccent),
                    )
                }
            }
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
    var selectedButton by remember { mutableIntStateOf(0) }

    LaunchedEffect(focusRequester) {
        delay(50.milliseconds)
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
internal fun EventSportPlaceholder(item: CatalogItem) {
    val category = listOf(item.title, item.subtitle, item.group, item.description).joinToString(" ").lowercase()
    val text = java.text.Normalizer.normalize(category, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
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
        Text(
            text = item.group.ifBlank { "EVENTO" }.uppercase().take(18),
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun CatalogItem.resolveDisplayTitle(): String = when {
    !tmdbTitle.isNullOrBlank() -> tmdbTitle
    kind == ContentKind.SERIES && !seriesName.isNullOrBlank() -> seriesName
    else -> normalizedTitle?.takeUnless { it.equals("null", ignoreCase = true) }?.takeIf { it.isNotBlank() }
        ?: title.takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
}

private fun CatalogItem.isHeroContent(): Boolean = isVodContent() || kind == ContentKind.EVENT

private fun CatalogItem.eventCompetitionText(): String {
    if (kind != ContentKind.EVENT) return ""
    val withoutTime = subtitle
        .replace(badgeText, "")
        .replace("  •  ", " ")
        .replace(" • ", " ")
        .replace("•", " ")
        .trim()
    return withoutTime.replace(Regex("\\s+"), " ")
}

private val REDUNDANT_BADGES = setOf("CINE", "SERIE", "Pelicula", "Serie")
