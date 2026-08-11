package com.example.walactv.ui.compose

import android.util.Log
import android.widget.ImageView.ScaleType.CENTER_CROP
import android.widget.ImageView.ScaleType.FIT_CENTER
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.example.walactv.BuildConfig
import com.example.walactv.R
import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.model.preferredCardImageUrl
import com.example.walactv.data.remote.api.dto.WatchProgressDto
import com.example.walactv.ui.fragment.ComposeMainFragment
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
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

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
    onMenuRequest: ((CatalogItem) -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    var keyDownMillis by remember { mutableLongStateOf(0L) }
    var consumeClick by remember { mutableStateOf(false) }
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

    val clickModifier = if (onMenuRequest != null) {
        Modifier
            .clickable { if (!consumeClick) onClick() }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter) &&
                    !consumeClick
                ) {
                    onClick()
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
                            if (elapsed >= LONG_PRESS_THRESHOLD_MS) {
                                consumeClick = true
                                onMenuRequest(item)
                                true
                            } else {
                                consumeClick = false
                                false
                            }
                        }
                        else -> false
                    }
                } else false
            }
    } else {
        Modifier.tvClickable { onClick() }
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
        .then(clickModifier)

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

// ── UFC card ───────────────────────────────────────────────────────────────

@Composable
internal fun UfcCard(
    item: CatalogItem,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) IptvFocusBorder else IptvSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (item.imageUrl.isNotBlank()) {
            RemoteImage(
                url = item.imageUrl,
                width = 300,
                height = 450,
                scaleType = CENTER_CROP,
            )
        } else {
            EventSportPlaceholder(item)
        }

        // Badge overlay: "UFC" at top-start
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .background(IptvLive.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = "UFC",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Bottom gradient overlay
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

        // Title at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, IptvFocusBorder, RoundedCornerShape(10.dp)),
            )
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
    onMenuRequest: (CatalogItem) -> Unit,
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
                            if (elapsed >= LONG_PRESS_THRESHOLD_MS) {
                                consumeClick = true
                                onMenuRequest(item)
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

// ── Continue watching options menu ─────────────────────────────────────────

@Composable
internal fun ContinueWatchingOptionsMenu(
    fragment: ComposeMainFragment,
    item: CatalogItem,
    progress: WatchProgressDto,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        stringResource(R.string.cw_menu_go_to_details),
        stringResource(R.string.cw_menu_play_from_start),
        stringResource(R.string.cw_menu_mark_watched),
        stringResource(R.string.cw_menu_clear_progress),
    )
    val displayTitle = if (item.kind == ContentKind.SERIES) {
        progress.seriesName?.takeIf { it.isNotBlank() }
            ?: item.seriesName?.takeIf { it.isNotBlank() }
            ?: item.title
    } else {
        item.title
    }
    val episodeLabel = if (item.kind == ContentKind.SERIES) {
        buildEpisodeLabel(
            season = progress.seasonNumber ?: item.seasonNumber,
            episode = progress.episodeNumber ?: item.episodeNumber,
        )
    } else {
        ""
    }

    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(50.milliseconds)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> {
                            selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                            true
                        }
                        Key.DirectionDown -> {
                            selectedIndex = (selectedIndex + 1).coerceAtMost(options.lastIndex)
                            true
                        }
                        Key.DirectionCenter,
                        Key.Enter -> {
                            when (selectedIndex) {
                                0 -> fragment.openContinueWatchingDetails(item, progress)
                                1 -> fragment.openContinueWatchingFromStart(item, progress)
                                2 -> fragment.markContinueWatchingAsWatched(item, progress)
                                3 -> fragment.clearContinueWatchingProgress(item)
                            }
                            onDismiss()
                            true
                        }
                        Key.Back,
                        Key.Escape -> { onDismiss(); true }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(380.dp)
                    .background(Color(0xFF1A1A2E), RoundedCornerShape(16.dp))
                    .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(16.dp))
                    .padding(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.cw_menu_title),
                        color = IptvTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        displayTitle,
                        color = IptvTextMuted,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (episodeLabel.isNotBlank()) {
                        Text(
                            episodeLabel,
                            color = IptvTextMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    options.forEachIndexed { index, label ->
                        val isSelected = index == selectedIndex
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) IptvFocusBg else Color.Transparent)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) IptvFocusBorder else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .tvClickable {
                                    val currentProgress = progress ?: return@tvClickable
                                    when (index) {
                                        0 -> fragment.openContinueWatchingDetails(item, currentProgress)
                                        1 -> fragment.openContinueWatchingFromStart(item, currentProgress)
                                        2 -> fragment.markContinueWatchingAsWatched(item, currentProgress)
                                        3 -> fragment.clearContinueWatchingProgress(item)
                                    }
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                label,
                                color = if (isSelected) IptvTextPrimary else IptvTextMuted,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── VOD options menu (Home / Discover long-press) ─────────────────────────

@Composable
internal fun VodOptionsMenu(
    fragment: ComposeMainFragment,
    item: CatalogItem,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        stringResource(R.string.cw_menu_go_to_details),
        stringResource(R.string.vod_menu_mark_watched),
    )

    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(50.milliseconds)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> {
                            selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                            true
                        }
                        Key.DirectionDown -> {
                            selectedIndex = (selectedIndex + 1).coerceAtMost(options.lastIndex)
                            true
                        }
                        Key.DirectionCenter,
                        Key.Enter -> {
                            when (selectedIndex) {
                                0 -> fragment.handleCardClick(item)
                                1 -> fragment.markCatalogItemAsWatched(item)
                            }
                            onDismiss()
                            true
                        }
                        Key.Back,
                        Key.Escape -> { onDismiss(); true }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(380.dp)
                    .background(Color(0xFF1A1A2E), RoundedCornerShape(16.dp))
                    .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(16.dp))
                    .padding(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.vod_menu_title),
                        color = IptvTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        item.title,
                        color = IptvTextMuted,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    options.forEachIndexed { index, label ->
                        val isSelected = index == selectedIndex
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) IptvFocusBg else Color.Transparent)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) IptvFocusBorder else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .tvClickable {
                                    when (index) {
                                        0 -> fragment.handleCardClick(item)
                                        1 -> fragment.markCatalogItemAsWatched(item)
                                    }
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                label,
                                color = if (isSelected) IptvTextPrimary else IptvTextMuted,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}
