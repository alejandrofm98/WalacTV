package com.example.walactv.ui.compose

import android.util.Log
import android.widget.ImageView.ScaleType.CENTER_CROP
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.walactv.BuildConfig
import com.example.walactv.data.model.BrowseSection
import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.model.isVodContent
import com.example.walactv.data.model.preferredVodPosterUrl
import com.example.walactv.ui.fragment.ComposeMainFragment
import com.example.walactv.ui.fragment.tmdbDebug
import com.example.walactv.ui.theme.IptvAccent
import com.example.walactv.ui.theme.IptvBackground
import com.example.walactv.ui.theme.IptvTextAccent
import com.example.walactv.ui.theme.IptvTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

// Anchos del rail lateral (deben coincidir con SideRail.kt)
private val SIDE_RAIL_COLLAPSED_WIDTH = 78.dp
private val SIDE_RAIL_EXPANDED_WIDTH  = 248.dp

// ── Home screen ────────────────────────────────────────────────────────────

@Composable
internal fun HomeContent(fragment: ComposeMainFragment) {
    val sectionFrMap = remember { mutableMapOf<String, FocusRequester>() }
    LaunchedEffect(fragment.homeSections.map { it.title }) {
        val titles = fragment.homeSections.map { it.title }.toSet()
        sectionFrMap.keys.removeAll { it !in titles }
        titles.forEach { title -> sectionFrMap.getOrPut(title) { FocusRequester() } }
    }

    LaunchedEffect(fragment.homeSections) {
        if (fragment.homeSections.isEmpty()) return@LaunchedEffect
        Log.d("HomeContent", "=== SECTIONS ORDER (${fragment.homeSections.size}) ===")
        fragment.homeSections.forEachIndexed { i, s ->
            Log.d("HomeContent", "  [$i] '${s.title}' items=${s.items.size}")
        }
        if (fragment.selectedHero != null || fragment.pendingFocusItem != null) return@LaunchedEffect
        delay(200.milliseconds)
        runCatching { sectionFrMap.values.firstOrNull()?.requestFocus() }
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
                    key(section.title) {
                        val sectionFr = remember(section.title) { sectionFrMap.getOrPut(section.title) { FocusRequester() } }
                        ContentSection(
                            fragment = fragment,
                            section = section,
                            sectionIndex = index,
                            selfFocusRequester = sectionFr,
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
                                            val contentType = sectionToLoad.contentType ?: return@launch
                                            val (newItems, hasNext) = fragment.repository.loadContentPage(
                                                contentType, sectionToLoad.groupName, nextPage, pageSize, sectionToLoad.year, sectionToLoad.sectionTitle
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
    }

    fragment.continueWatchingMenuItem?.let { item ->
        val progress = fragment.continueWatchingEntries[item.stableId]
        if (progress != null) {
            ContinueWatchingOptionsMenu(
                fragment = fragment,
                item = item,
                progress = progress,
                onDismiss = { fragment.continueWatchingMenuItem = null },
            )
        } else {
            fragment.continueWatchingMenuItem = null
        }
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
            item?.voteAverage?.takeIf { it > 0.0 }?.let { rating ->
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
