package com.example.walactv.ui.compose

import android.util.Log
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.walactv.ui.compose.NativeSearchBar
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.walactv.data.model.AppUpdateAvailability
import com.example.walactv.data.remote.api.dto.FilterOptionDto
import com.example.walactv.ui.compose.ChangelogDialog
import com.example.walactv.ui.fragment.ComposeMainFragment
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.model.InstalledAppVersion
import com.example.walactv.data.preferences.PreferencesManager
import com.example.walactv.data.model.evaluateAppUpdate
import com.example.walactv.ui.theme.*

// ── Settings screen ────────────────────────────────────────────────────────

@Composable
internal fun SettingsContent(fragment: ComposeMainFragment) {
    var preferredLanguage by remember { mutableStateOf(PreferencesManager.getPreferredLanguageOrDefault()) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    val availableLanguages = listOf("ES" to "Español", "EN" to "Inglés")
    val installedVersionLabel = fragment.installedAppVersion?.let { it.versionName } ?: "Desconocida"
    val hasUpdate = fragment.availableUpdate?.let { evaluateAppUpdate(fragment.installedAppVersion ?: InstalledAppVersion("0", 0), it) != AppUpdateAvailability.UP_TO_DATE } == true
    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(fragment.contentFocusTrigger) {
        if (fragment.contentFocusTrigger == 0) return@LaunchedEffect
        runCatching { firstFocusRequester.requestFocus() }
            .onSuccess { Log.d("MainShellFocus", "settings first row requestFocus success") }
            .onFailure { Log.w("MainShellFocus", "settings first row requestFocus failed: ${it.message}") }
    }

    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ScreenHeader(title = "Ajustes", subtitle = "Actualizaciones, idioma y sesion")
        Column(
            modifier = Modifier.width(760.dp).background(IptvSurface, RoundedCornerShape(10.dp))
                .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(10.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val selectedLanguageLabel = availableLanguages.find { it.first == preferredLanguage }?.second ?: "Español"
            SettingsRowClickable(
                label = "Idioma preferido",
                value = selectedLanguageLabel,
                modifier = Modifier.focusRequester(firstFocusRequester),
            ) { showLanguageDialog = true }
            SettingsRow("Version de la app", installedVersionLabel)
            val channelsCount by produceState(initialValue = -1) {
                value = fragment.contentCacheManager.getChannelsTotalCount(null, null)
            }
            SettingsRow("Canales cargados", if (channelsCount >= 0) channelsCount.toString() else "...")
            fragment.updateErrorMessage?.let { Text(it, color = IptvLive, fontSize = 14.sp, modifier = Modifier.focusable()) }
            val update = fragment.availableUpdate
            val statusText = when {
                fragment.isUpdateDownloading -> "Descargando actualizacion..."
                fragment.isCheckingUpdates -> "Buscando actualizaciones..."
                hasUpdate && update != null -> "Nueva version disponible: v${update.latestVersionName}"
                update != null -> "Aplicacion actualizada"
                else -> "No comprobado"
            }
            Text(statusText, color = if (hasUpdate) IptvAccent else IptvOnline, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.focusable())
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val changelogText = update?.changelog
                if (!changelogText.isNullOrBlank()) {
                    FocusButton(label = "Ver novedades", icon = Icons.Outlined.Info, modifier = Modifier.weight(1f)) { showChangelogDialog = true }
                }
                FocusButton(label = "Cerrar sesion", icon = Icons.Outlined.Settings, modifier = Modifier.weight(1f)) { fragment.performSignOut() }
            }
        }
    }

    if (showLanguageDialog) {
        FilterDialog(
            title = "Idioma preferido",
            options = availableLanguages.map { FilterOptionDto(value = it.first, label = it.second) },
            selectedOption = preferredLanguage,
            onOptionSelected = { PreferencesManager.preferredLanguage = it.value; preferredLanguage = it.value; showLanguageDialog = false },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showChangelogDialog) {
        fragment.composeDialogOpen = true
        val update = fragment.availableUpdate ?: fragment.mandatoryUpdate
        ChangelogDialog(
            versionName = update?.latestVersionName ?: fragment.installedAppVersion?.versionName ?: "Desconocida",
            markdown = update?.changelog?.ifBlank { "Sin notas de la version." } ?: "No hay informacion disponible.",
            onDismiss = { showChangelogDialog = false; fragment.composeDialogOpen = false },
        )
    }
}

@Composable
internal fun SettingsRowClickable(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth()
            .background(if (isFocused) IptvFocusBg else Color.Transparent, RoundedCornerShape(8.dp))
            .border(1.dp, if (isFocused) IptvFocusBorder else Color.Transparent, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }.tvClickable { onClick() }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = IptvTextPrimary, fontSize = 16.sp)
        Text("$value ▸", color = IptvAccent, fontSize = 16.sp)
    }
}

@Composable
internal fun SettingsRow(label: String, value: String) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .background(if (isFocused) IptvFocusBg else Color.Transparent, RoundedCornerShape(8.dp))
            .border(1.dp, if (isFocused) IptvFocusBorder else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = IptvTextPrimary, fontSize = 16.sp)
        Text(value, color = IptvTextMuted, fontSize = 16.sp)
    }
}

// ── Shared UI widgets ──────────────────────────────────────────────────────

@Composable
internal fun FocusButton(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.height(52.dp)
            .background(if (isFocused) IptvAccent else IptvCard, RoundedCornerShape(8.dp))
            .border(if (isFocused) 2.dp else 1.dp, if (isFocused) IptvTextPrimary else IptvSurfaceVariant, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }.tvClickable { onClick() }.padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = IptvTextPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = IptvTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun PlaceholderIcon(kind: ContentKind, size: Dp = 32.dp) {
    val icon = when (kind) {
        ContentKind.EVENT   -> Icons.Outlined.Event
        ContentKind.CHANNEL -> Icons.Outlined.LiveTv
        ContentKind.MOVIE   -> Icons.Outlined.Movie
        ContentKind.SERIES  -> Icons.Outlined.Tv
    }
    Icon(icon, contentDescription = null, tint = IptvTextMuted, modifier = Modifier.size(size))
}

@Composable
internal fun ScreenHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = IptvTextPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotBlank()) Text(subtitle, color = IptvTextMuted, fontSize = 16.sp)
    }
}

@Composable
internal fun RemoteImage(
    url: String,
    width: Int,
    height: Int,
    scaleType: ImageView.ScaleType,
    disableCache: Boolean = false,
    adjustViewBounds: Boolean = false,
) {
    val contentScale = when (scaleType) {
        ImageView.ScaleType.CENTER_CROP -> ContentScale.Crop
        ImageView.ScaleType.FIT_CENTER -> ContentScale.Fit
        ImageView.ScaleType.CENTER_INSIDE -> ContentScale.Fit
        else -> ContentScale.Crop
    }

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .apply {
                if (disableCache) {
                    memoryCachePolicy(CachePolicy.DISABLED)
                    diskCachePolicy(CachePolicy.DISABLED)
                }
            }
            .build(),
        contentDescription = null,
        contentScale = contentScale,
        modifier = if (adjustViewBounds) Modifier.fillMaxHeight() else Modifier.fillMaxSize(),
    )
}

// ── Filter top bar ─────────────────────────────────────────────────────────

@Composable
internal fun FilterTopBar(
    showIdioma: Boolean,
    selectedIdioma: String,
    selectedGrupo: String,
    onIdiomaClicked: () -> Unit,
    onGrupoClicked: () -> Unit,
    idiomaFocusRequester: FocusRequester,
    grupoFocusRequester: FocusRequester,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchFocusRequester: FocusRequester,
    onSearchImeDismissed: () -> Unit = {},
    idiomaLabel: String = "País",
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showIdioma) {
            FilterChip(label = "$idiomaLabel: $selectedIdioma", focusRequester = idiomaFocusRequester, onClick = onIdiomaClicked)
        }
        FilterChip(label = "Grupo: $selectedGrupo", focusRequester = grupoFocusRequester, onClick = onGrupoClicked)
        Spacer(Modifier.weight(1f))
        SearchBar(query = searchQuery, onQueryChange = onSearchQueryChange, focusRequester = searchFocusRequester, onImeDismissed = onSearchImeDismissed)
    }
}

@Composable
internal fun FilterChip(label: String, focusRequester: FocusRequester, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.height(40.dp)
            .background(if (isFocused) IptvFocusBg else IptvBackground, RoundedCornerShape(8.dp))
            .border(if (isFocused) 2.dp else 1.dp, if (isFocused) IptvFocusBorder else IptvSurfaceVariant, RoundedCornerShape(8.dp))
            .focusRequester(focusRequester).onFocusChanged { isFocused = it.isFocused }
            .tvClickable { onClick() }.padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, color = if (isFocused) IptvTextPrimary else IptvTextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = if (isFocused) IptvTextPrimary else IptvTextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
internal fun SearchBar(query: String, onQueryChange: (String) -> Unit, focusRequester: FocusRequester, onImeDismissed: () -> Unit = {}) {
    NativeSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        focusRequester = focusRequester,
        onImeDismissed = onImeDismissed,
        modifier = Modifier.width(260.dp),
    )
}


