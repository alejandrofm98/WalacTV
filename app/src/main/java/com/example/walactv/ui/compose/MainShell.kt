package com.example.walactv.ui.compose

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.example.walactv.BuildConfig
import com.example.walactv.ComposeMainFragment
import com.example.walactv.ComposeMainFragment.ContentSyncState
import com.example.walactv.ComposeMainFragment.MainMode
import com.example.walactv.ContentKind
import com.example.walactv.R
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
                mandatoryUpdate != null                           -> MandatoryUpdateScreen(fragment, mandatoryUpdate!!)
                !isSignedIn                                      -> LoginScreen(fragment)
                errorMessage != null                             -> ErrorScreen(fragment, errorMessage.orEmpty())
                contentSyncState == ContentSyncState.ERROR && !isLoaded ->
                    ErrorScreen(fragment, contentSyncError ?: "Error al sincronizar contenido")
                !isLoaded                                        -> LoadingScreen()
                else                                             -> MainShell(fragment)
            }
        }
    }
}

// ── Main shell ─────────────────────────────────────────────────────────────

@Composable
internal fun MainShell(fragment: ComposeMainFragment) {
    val railItems = remember { buildDefaultSideRailEntries().map { entry -> fragment.toNavItem(entry) } }
    val focusRequesters = remember { List(railItems.size + 1) { FocusRequester() } }
    val contentFocusRequester = remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            SideRail(fragment, railItems, focusRequesters)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(contentFocusRequester)
                    .onFocusChanged { state ->
                        if (BuildConfig.DEBUG) Log.d(TAG, "content focus isFocused=${state.isFocused} hasFocus=${state.hasFocus} mode=${fragment.currentMode}")
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN)
                            return@onPreviewKeyEvent false

                        if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                            if (fragment.currentMode == MainMode.Home) {
                                val homeTarget = fragment.lastHomeFocusTarget ?: return@onPreviewKeyEvent false
                                if (homeTarget.itemIndex > 0) return@onPreviewKeyEvent false
                            } else if (!fragment.contentFocusCanOpenRail) {
                                return@onPreviewKeyEvent false
                            }

                            val index = railItems.indexOfFirst {
                                it.mode != null && fragment.currentMode == it.mode
                            }
                            val target = when {
                                index >= 0                                -> focusRequesters[index]
                                fragment.currentMode == MainMode.Settings -> focusRequesters.last()
                                else                                      -> focusRequesters.first()
                            }
                            runCatching { target.requestFocus() }
                            true
                        } else false
                    },
            ) {
                when (fragment.currentMode) {
                    MainMode.Home     -> HomeContent(fragment)
                    MainMode.TV       -> GuideContent(fragment, ContentKind.CHANNEL)
                    MainMode.Events   -> GuideContent(fragment, ContentKind.EVENT)
                    MainMode.Discover -> DiscoverContent(fragment)
                    MainMode.Settings -> SettingsContent(fragment)
                }
            }
        }
        if (fragment.showChannelPicker) {
            Dialog(
                onDismissRequest = { fragment.showChannelPicker = false },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                ),
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
                    onChannelSelected = { item ->
                        fragment.playCatalogItem(item, 0)
                        fragment.showChannelPicker = false
                    },
                    onDismiss = { fragment.showChannelPicker = false },
                )
            }
        }
    }
}

// ── Side rail ──────────────────────────────────────────────────────────────

private val SIDE_RAIL_COLLAPSED_WIDTH = 78.dp
private val SIDE_RAIL_EXPANDED_WIDTH  = 248.dp
private const val TAG = "MainShellFocus"

@Composable
internal fun SideRail(
    fragment: ComposeMainFragment,
    railItems: List<ComposeMainFragment.NavItem>,
    focusRequesters: List<FocusRequester>,
) {
    val railWidth by animateDpAsState(
        targetValue = if (fragment.isRailExpanded) SIDE_RAIL_EXPANDED_WIDTH else SIDE_RAIL_COLLAPSED_WIDTH,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "railWidthAnim",
    )

    Column(
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight()
            .background(IptvSidebarBg)
            .onFocusChanged { state ->
                fragment.isRailExpanded = state.hasFocus
                if (BuildConfig.DEBUG) Log.d(TAG, "rail focus hasFocus=${state.hasFocus} mode=${fragment.currentMode}")
            }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN)
                    return@onPreviewKeyEvent false

                if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    Log.d(TAG, "DPAD_RIGHT rail mode=${fragment.currentMode} expanded=${fragment.isRailExpanded}")
                    fragment.isRailExpanded = false
                    if (fragment.currentMode == MainMode.Home) {
                        val restored = fragment.requestHomeFocusRestoreFromRail()
                        Log.d(TAG, "home explicit restore requested restored=$restored")
                        return@onPreviewKeyEvent true
                    }

                    fragment.contentFocusTrigger++
                    Log.d(TAG, "content focus trigger=${fragment.contentFocusTrigger} mode=${fragment.currentMode}")
                    true
                } else false
            },
    ) {
        RailHeader(expanded = fragment.isRailExpanded)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            railItems.forEachIndexed { index, item ->
                NavigationItem(
                    icon = item.icon,
                    label = item.label,
                    selected = item.mode != null && fragment.currentMode == item.mode,
                    expanded = fragment.isRailExpanded,
                    modifier = Modifier.focusRequester(focusRequesters[index]),
                onFocusChanged = { focused ->
                    if (BuildConfig.DEBUG) Log.d(TAG, "rail item focus label=${item.label} focused=$focused mode=${fragment.currentMode}")
                }
                ) { item.onClick?.invoke() ?: item.mode?.let(fragment::changeMode) }
            }
        }
        Box(modifier = Modifier.padding(6.dp)) {
            NavigationItem(
                icon = Icons.Outlined.Settings,
                label = "Ajustes",
                selected = fragment.currentMode == MainMode.Settings,
                expanded = fragment.isRailExpanded,
                modifier = Modifier.focusRequester(focusRequesters.last()),
                onFocusChanged = { focused ->
                    if (BuildConfig.DEBUG) Log.d(TAG, "rail item focus label=Ajustes focused=$focused mode=${fragment.currentMode}")
                }
            ) { fragment.changeMode(MainMode.Settings) }
        }
    }
}

@Composable
private fun RailHeader(expanded: Boolean) {
    Box(modifier = Modifier.height(80.dp)) {
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    "WalacTV",
                    color = IptvTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun ComposeMainFragment.toNavItem(entry: SideRailEntry): ComposeMainFragment.NavItem {
    return when (entry.destination) {
        SideRailDestination.SEARCH   -> ComposeMainFragment.NavItem(Icons.Outlined.Search, entry.label, onClick = ::openSearch)
        SideRailDestination.HOME     -> ComposeMainFragment.NavItem(Icons.Outlined.Home, entry.label, MainMode.Home)
        SideRailDestination.EVENTS   -> ComposeMainFragment.NavItem(Icons.Outlined.Event, entry.label, MainMode.Events)
        SideRailDestination.TV       -> ComposeMainFragment.NavItem(Icons.Outlined.LiveTv, entry.label, MainMode.TV)
        SideRailDestination.DISCOVER -> ComposeMainFragment.NavItem(Icons.Outlined.Explore, entry.label, MainMode.Discover)
        else                         -> ComposeMainFragment.NavItem(Icons.Outlined.Home, entry.label, MainMode.Home)
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
    onFocusChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor      = when { isFocused -> IptvFocusBg; selected -> IptvSidebarSelected; else -> Color.Transparent }
    val borderColor  = when { isFocused -> IptvFocusBorder; selected -> Color.Transparent; else -> Color.Transparent }
    val contentColor = if (isFocused || selected) IptvTextPrimary else IptvTextMuted

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(bgColor, RoundedCornerShape(percent = 50))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(percent = 50))
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .tvClickable { onClick() }
            .padding(horizontal = if (expanded) 14.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(18.dp))
        AnimatedVisibility(visible = expanded, enter = fadeIn(tween(300)), exit = fadeOut(tween(150))) {
            Row {
                Spacer(Modifier.width(12.dp))
                Text(
                    label,
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Loading / Error / Sync screens ────────────────────────────────────────

@Composable
internal fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.logo_letra),
            contentDescription = "WalacTV",
            modifier = Modifier.fillMaxWidth(0.5f),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
internal fun ErrorScreen(fragment: ComposeMainFragment, message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(620.dp)
                .background(IptvSurface, RoundedCornerShape(10.dp))
                .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(10.dp))
                .padding(28.dp),
        ) {
            Text("No se pudo cargar WalacTV", color = IptvTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(message, color = IptvTextMuted, fontSize = 18.sp)
            Spacer(Modifier.height(24.dp))
            FocusButton(label = "Reintentar", icon = Icons.Outlined.PlayArrow) { fragment.viewModel.startLoad(forceRefresh = true) }
        }
    }
}
