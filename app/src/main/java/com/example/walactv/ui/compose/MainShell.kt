package com.example.walactv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.example.walactv.ComposeMainFragment
import com.example.walactv.ComposeMainFragment.MainMode
import com.example.walactv.ContentKind
import com.example.walactv.SideRailDestination
import com.example.walactv.SideRailEntry
import com.example.walactv.buildDefaultSideRailEntries
import com.example.walactv.ui.theme.*

// ── Root ───────────────────────────────────────────────────────────────────

@Composable
internal fun ComposeRoot(fragment: ComposeMainFragment) {
    Box(modifier = Modifier.fillMaxSize().background(IptvBackground)) {
        with(fragment) {
            when {
                mandatoryUpdate != null -> MandatoryUpdateScreen(fragment, mandatoryUpdate!!)
                !isSignedIn             -> LoginScreen(fragment)
                errorMessage != null    -> ErrorScreen(fragment, errorMessage.orEmpty())
                contentSyncState == ComposeMainFragment.ContentSyncState.SYNCING ||
                contentSyncState == ComposeMainFragment.ContentSyncState.CHECKING -> SyncScreen(fragment)
                !isLoaded               -> LoadingScreen()
                else                    -> MainShell(fragment)
            }
        }
    }
}

// ── Main shell ─────────────────────────────────────────────────────────────

@Composable
internal fun MainShell(fragment: ComposeMainFragment) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            SideRail(fragment)
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (fragment.currentMode) {
                    MainMode.Home     -> HomeContent(fragment)
                    MainMode.TV       -> GuideContent(fragment, ContentKind.CHANNEL)
                    MainMode.Events   -> GuideContent(fragment, ContentKind.EVENT)
                    MainMode.Movies   -> VodGridContent(fragment, ContentKind.MOVIE)
                    MainMode.Series   -> VodGridContent(fragment, ContentKind.SERIES)
                    MainMode.Settings -> SettingsContent(fragment)
                    MainMode.Anime    -> AnimeBrowseContent(fragment)
                }
            }
        }
        if (fragment.showChannelPicker) {
            Dialog(
                onDismissRequest = { fragment.showChannelPicker = false },
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
            ) {
                ChannelPickerDialog(
                    fragment = fragment,
                    currentCountry = fragment.channelPickerCountry,
                    currentGroup = fragment.channelPickerGroup,
                    searchQuery = fragment.channelPickerQuery,
                    showFavorites = fragment.channelPickerShowFavorites,
                    onCountryChange = { fragment.channelPickerCountry = it },
                    onGroupChange = { fragment.channelPickerGroup = it },
                    onFavoritesChange = { fragment.channelPickerShowFavorites = it },
                    onSearchChange = { fragment.channelPickerQuery = it },
                    onChannelSelected = { item -> fragment.playCatalogItem(item, 0); fragment.showChannelPicker = false },
                    onDismiss = { fragment.showChannelPicker = false },
                )
            }
        }
    }
}

// ── Side rail ──────────────────────────────────────────────────────────────

@Composable
internal fun SideRail(fragment: ComposeMainFragment) {
    val railItems = buildDefaultSideRailEntries().map { entry -> fragment.toNavItem(entry) }
    val focusRequesters = remember { List(railItems.size + 1) { FocusRequester() } }

    val railWidth by animateDpAsState(
        targetValue = if (fragment.isRailExpanded) 248.dp else 78.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "railWidthAnim",
    )

    Column(
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight()
            .background(IptvSurface)
            .border(1.dp, IptvSurfaceVariant)
            .focusable()
            .onFocusChanged { fragment.isRailExpanded = it.hasFocus }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                ) {
                    val index = railItems.indexOfFirst { it.mode != null && fragment.currentMode == it.mode }
                    if (index >= 0 && index < focusRequesters.size) runCatching { focusRequesters[index].requestFocus() }
                    else if (focusRequesters.isNotEmpty()) runCatching { focusRequesters.last().requestFocus() }
                    true
                } else false
            },
    ) {
        Box(modifier = Modifier.height(80.dp)) {
            if (fragment.isRailExpanded) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text("WalacTV", color = IptvTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text("Navegacion", color = IptvTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            railItems.forEachIndexed { index, item ->
                NavigationItem(
                    icon = item.icon, label = item.label,
                    selected = item.mode != null && fragment.currentMode == item.mode,
                    expanded = fragment.isRailExpanded,
                    modifier = Modifier.focusRequester(focusRequesters[index]),
                ) { item.onClick?.invoke() ?: item.mode?.let(fragment::changeMode) }
            }
        }
        Box(modifier = Modifier.padding(6.dp)) {
            NavigationItem(
                icon = Icons.Outlined.Settings, label = "Ajustes",
                selected = fragment.currentMode == MainMode.Settings,
                expanded = fragment.isRailExpanded,
                modifier = Modifier.focusRequester(focusRequesters.last()),
            ) { fragment.changeMode(MainMode.Settings) }
        }
    }
}

internal fun ComposeMainFragment.toNavItem(entry: SideRailEntry): ComposeMainFragment.NavItem {
    return when (entry.destination) {
        SideRailDestination.SEARCH -> ComposeMainFragment.NavItem(Icons.Outlined.Search, entry.label, onClick = ::openSearch)
        SideRailDestination.HOME   -> ComposeMainFragment.NavItem(Icons.Outlined.Home, entry.label, MainMode.Home)
        SideRailDestination.EVENTS -> ComposeMainFragment.NavItem(Icons.Outlined.Event, entry.label, MainMode.Events)
        SideRailDestination.TV     -> ComposeMainFragment.NavItem(Icons.Outlined.LiveTv, entry.label, MainMode.TV)
        SideRailDestination.MOVIES -> ComposeMainFragment.NavItem(Icons.Outlined.Movie, entry.label, MainMode.Movies)
        SideRailDestination.SERIES -> ComposeMainFragment.NavItem(Icons.Outlined.Tv, entry.label, MainMode.Series)
        SideRailDestination.ANIME  -> ComposeMainFragment.NavItem(Icons.Outlined.PlayArrow, entry.label, MainMode.Anime)
    }
}

// ── Navigation item ────────────────────────────────────────────────────────

@Composable
internal fun NavigationItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor = when { isFocused -> IptvFocusBg; selected -> IptvCard; else -> Color.Transparent }
    val borderColor = when { isFocused -> IptvFocusBorder; selected -> IptvSurfaceVariant; else -> Color.Transparent }
    val contentColor = if (isFocused || selected) IptvTextPrimary else IptvTextMuted

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = if (expanded) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(18.dp))
        AnimatedVisibility(visible = expanded, enter = fadeIn(tween(300)), exit = fadeOut(tween(150))) {
            Row {
                Spacer(Modifier.width(12.dp))
                Text(label, color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ── Loading / Error / Sync screens ────────────────────────────────────────

@Composable
internal fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("WalacTV", color = IptvTextPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text("Cargando contenido...", color = IptvTextMuted, fontSize = 18.sp)
            Spacer(Modifier.height(18.dp))
            Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(IptvSurfaceVariant, RoundedCornerShape(6.dp))) {
                Box(modifier = Modifier.fillMaxWidth(0.04f).height(10.dp).background(IptvAccent, RoundedCornerShape(6.dp)))
            }
        }
    }
}

@Composable
internal fun ErrorScreen(fragment: ComposeMainFragment, message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(620.dp).background(IptvSurface, RoundedCornerShape(10.dp))
                .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(10.dp)).padding(28.dp),
        ) {
            Text("No se pudo cargar WalacTV", color = IptvTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(message, color = IptvTextMuted, fontSize = 18.sp)
            Spacer(Modifier.height(24.dp))
            FocusButton(label = "Reintentar", icon = Icons.Outlined.PlayArrow) { fragment.startLoad() }
        }
    }
}
