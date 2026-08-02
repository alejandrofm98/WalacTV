package com.example.walactv.ui.compose

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.walactv.BuildConfig
import com.example.walactv.data.model.BrowseSection
import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.remote.api.dto.progressPercent
import com.example.walactv.ui.fragment.ComposeMainFragment
import com.example.walactv.ui.theme.IptvAccent
import com.example.walactv.ui.theme.IptvBackground
import com.example.walactv.ui.theme.IptvCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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
    val itemFrMap = remember { mutableMapOf<String, FocusRequester>() }
    LaunchedEffect(section.items.map { it.stableId }) {
        val keys = section.items.map { it.stableId }.toSet()
        itemFrMap.keys.removeAll { it !in keys }
        keys.forEach { key -> itemFrMap.getOrPut(key) { FocusRequester() } }
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
                section.items.getOrNull(index)?.stableId?.let { sid -> itemFrMap[sid] }?.requestFocus()
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
                    val fr = itemFrMap[targetId]
                    if (fr != null) {
                        fr.requestFocus()
                        Log.d("HomeContent", "Focus RESTORED: ${section.title}[$idx] on attempt $attempt")
                        fragment.pendingFocusItem = null
                        fragment.suppressEventAutoScroll = false
                        break
                    } else {
                        Log.w("HomeContent", "FocusRequester for $targetId is null, retry...")
                    }
                } catch (e: Exception) {
                    Log.w("HomeContent", "Focus restore FAILED attempt $attempt: ${e.message}")
                }
            } else {
                Log.d("HomeContent", "Item $targetId NOT FOUND in section '${section.title}'")
                if (sectionIndex == 0 && section.items.isNotEmpty() && itemFrMap.isNotEmpty()) {
                    Log.d("HomeContent", "First section fallback: focusing first card")
                    runCatching {
                        lazyListState.scrollToItem(0)
                        delay(80.milliseconds)
                        section.items.firstOrNull()?.stableId?.let { sid -> itemFrMap[sid] }?.requestFocus()
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
            Log.d("HomeContent", "Rail restore has no target, focusing initialIndex=$initialIndex section=${section.title} items=${section.items.size}")
            runCatching {
                lazyListState.scrollToItem(initialIndex)
                delay(80.milliseconds)
                val requester = section.items.getOrNull(initialIndex)?.stableId?.let { sid -> itemFrMap[sid] }
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

        runCatching {
            val idx = section.items.indexOfFirst { it.stableId == target.itemStableId }
            if (idx >= 0) lazyListState.scrollToItem(idx)
            delay(80.milliseconds)
            itemFrMap[target.itemStableId]?.requestFocus()
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
                    val fr = remember(item.stableId) { itemFrMap.getOrPut(item.stableId) { FocusRequester() } }
                    val cardModifier = Modifier.focusRequester(fr)

                    if (section.title == "Continuar viendo") {
                        val wp = cwLookup[item]
                        val remainingText = wp?.let { formatDurationRemaining(it.positionMs ?: 0L, it.durationMs ?: 0L) }
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
                            onMenuRequest = { fragment.continueWatchingMenuItem = it },
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
                                onMenuRequest = { fragment.catalogItemMenuItem = it },
                                onClick = { fragment.handleCardClick(item, section.items) },
                            )
                        }
                    }
                }
            }
        }
    }
}
