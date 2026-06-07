package com.example.walactv.ui.compose

import android.util.Log
import android.widget.ImageView
import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.walactv.NativeSearchBar
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
import com.example.walactv.AppUpdateAvailability
import com.example.walactv.CatalogFilterOption
import com.example.walactv.ChangelogDialog
import com.example.walactv.ComposeMainFragment
import com.example.walactv.ContentKind
import com.example.walactv.InstalledAppVersion
import com.example.walactv.PreferencesManager
import com.example.walactv.evaluateAppUpdate
import com.example.walactv.ui.theme.*
import kotlinx.coroutines.launch

// ── Settings screen ────────────────────────────────────────────────────────

@Composable
internal fun SettingsContent(fragment: ComposeMainFragment) {
    var preferredLanguage by remember { mutableStateOf(PreferencesManager.getPreferredLanguageOrDefault()) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    val availableLanguages = listOf("ES" to "Español", "EN" to "Inglés")
    val installedVersionLabel = fragment.installedAppVersion?.let { "${it.versionName} (${it.versionCode})" } ?: "Desconocida"
    val hasUpdate = fragment.availableUpdate?.let { evaluateAppUpdate(fragment.installedAppVersion ?: InstalledAppVersion("0", 0), it) != AppUpdateAvailability.UP_TO_DATE } == true
    val updateActionLabel = when { fragment.isUpdateDownloading -> "Descargando..."; fragment.isCheckingUpdates -> "Comprobando..."; hasUpdate -> "Descargar actualización"; else -> "Buscar actualizaciones" }
    val scope = rememberCoroutineScope()
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
                label = "Idioma de series",
                value = selectedLanguageLabel,
                modifier = Modifier.focusRequester(firstFocusRequester),
            ) { showLanguageDialog = true }
            SettingsRow("Version de la app", installedVersionLabel)
            SettingsRow("Canales cargados", fragment.channelLineup.size.toString())
            SettingsRow("Contenido indexado", fragment.searchableItems.size.toString())
            fragment.updateErrorMessage?.let { Text(it, color = IptvLive, fontSize = 14.sp) }
            val update = fragment.availableUpdate
            if (hasUpdate && update != null) Text("Ultima version: v${update.latestVersionName}", color = IptvAccent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            else if (update != null) Text("Actualizado a la ultima version", color = IptvOnline, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val changelogText = update?.changelog
                if (!changelogText.isNullOrBlank()) {
                    FocusButton(label = "Ver novedades", icon = Icons.Outlined.Info, modifier = Modifier.weight(1f)) { showChangelogDialog = true }
                }
                FocusButton(label = updateActionLabel, icon = Icons.Outlined.PlayArrow, modifier = Modifier.weight(1f)) {
                    if (!fragment.isUpdateDownloading && !fragment.isCheckingUpdates) {
                        scope.launch {
                            fragment.isCheckingUpdates = true
                            val remoteUpdate = runCatching { fragment.appUpdateRepository.fetchRemoteUpdate() }.getOrNull()
                            if (remoteUpdate != null) { fragment.appUpdateRepository.cacheUpdate(remoteUpdate); fragment.availableUpdate = remoteUpdate }
                            val installed = fragment.installedAppVersion
                            val latest = remoteUpdate ?: fragment.availableUpdate
                            fragment.isCheckingUpdates = false
                            if (latest == null || installed == null || evaluateAppUpdate(installed, latest) == AppUpdateAvailability.UP_TO_DATE) {
                                Toast.makeText(fragment.requireContext(), "Ya tienes la última versión instalada", Toast.LENGTH_SHORT).show()
                            } else {
                                fragment.startUpdateFlow()
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FocusButton(label = "Cerrar sesion", icon = Icons.Outlined.Settings) { fragment.performSignOut() }
            }
        }
    }

    if (showLanguageDialog) {
        FilterDialog(
            title = "Idioma preferido",
            options = availableLanguages.map { CatalogFilterOption(value = it.first, label = it.second) },
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
private fun FilterChip(label: String, focusRequester: FocusRequester, onClick: () -> Unit) {
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
    private fun SearchBar(query: String, onQueryChange: (String) -> Unit, focusRequester: FocusRequester, onImeDismissed: () -> Unit = {}) {
        NativeSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            focusRequester = focusRequester,
            onImeDismissed = onImeDismissed,
            modifier = Modifier.width(260.dp),
        )
    }

// ── Filter dialog ──────────────────────────────────────────────────────────

@Composable
internal fun FilterDialog(
    title: String,
    options: List<CatalogFilterOption>,
    selectedOption: String,
    onOptionSelected: (CatalogFilterOption) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.width(400.dp).heightIn(max = 500.dp)
                .background(IptvSurface, RoundedCornerShape(12.dp))
                .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(12.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = IptvTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(options, key = { it.value }) { option ->
                    var isFocused by remember { mutableStateOf(false) }
                    val isSelected = option.value == selectedOption
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(when { isFocused -> IptvFocusBg; isSelected -> IptvCard; else -> Color.Transparent }, RoundedCornerShape(8.dp))
                            .border(if (isFocused || isSelected) 1.dp else 0.dp, if (isFocused) IptvFocusBorder else if (isSelected) IptvSurfaceVariant else Color.Transparent, RoundedCornerShape(8.dp))
                            .onFocusChanged { isFocused = it.isFocused }.clickable { onOptionSelected(option) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(option.label, color = if (isFocused || isSelected) IptvTextPrimary else IptvTextSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
