@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.example.walactv

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.bumptech.glide.Glide
import com.example.walactv.ui.theme.IptvAccent
import com.example.walactv.ui.theme.IptvBackground
import com.example.walactv.ui.theme.IptvCard
import com.example.walactv.ui.theme.IptvFocusBg
import com.example.walactv.ui.theme.IptvFocusBorder
import com.example.walactv.ui.theme.IptvLive
import com.example.walactv.ui.theme.IptvSurface
import com.example.walactv.ui.theme.IptvSurfaceVariant
import com.example.walactv.ui.theme.IptvTextMuted
import com.example.walactv.ui.theme.IptvTextPrimary
import com.example.walactv.ui.theme.WalacTVTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ComposeMainFragment : Fragment() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: IptvRepository
    private lateinit var channelStateStore: ChannelStateStore

    private var homeCatalog by mutableStateOf<HomeCatalog?>(null)
    private var homeSections by mutableStateOf<List<BrowseSection>>(emptyList())
    private var searchableItems by mutableStateOf<List<CatalogItem>>(emptyList())
    private var channelLineup by mutableStateOf<List<CatalogItem>>(emptyList())
    private var selectedHero by mutableStateOf<CatalogItem?>(null)
    private var currentMode by mutableStateOf(MainMode.Home)
    private var selectedGroup by mutableStateOf(ALL_CHANNELS_GROUP)
    private var isRailExpanded by mutableStateOf(false)
    private var isSignedIn by mutableStateOf(false)
    private var loginUsername by mutableStateOf("")
    private var loginPassword by mutableStateOf("")
    private var loginError by mutableStateOf<String?>(null)
    private var isSigningIn by mutableStateOf(false)

    private var isEventsLoaded by mutableStateOf(false)
    private var isFullLoaded by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var isRefreshingInBackground by mutableStateOf(false)
    private var playlistProgress by mutableStateOf<PlaylistLoadProgress?>(null)

    private var currentItem: CatalogItem? = null
    private var currentStreamIndex: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        repository = IptvRepository(requireContext())
        repository.setPlaylistProgressListener { progress ->
            scope.launch(Dispatchers.Main) {
                playlistProgress = progress
            }
        }
        channelStateStore = ChannelStateStore(requireContext())
        isSignedIn = repository.hasStoredCredentials()
        loginUsername = repository.currentUsername()

        return ComposeView(requireContext()).apply {
            setContent {
                WalacTVTheme {
                    ComposeRoot()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (isSignedIn) {
            startProgressiveLoad()
        }
    }

    private fun startProgressiveLoad() {
        if (homeCatalog != null) return

        scope.launch {
            errorMessage = null
            loadFullPlaylist()
        }
    }

    private fun loadFullPlaylist(forceRefresh: Boolean = false) {
        scope.launch {
            runCatching {
                repository.loadHomeCatalog(
                    forceRefreshPlaylist = forceRefresh,
                )
            }
                .onSuccess { catalog ->
                    homeCatalog = catalog
                    updateStateFromCatalog(catalog)
                    isEventsLoaded = true
                    isFullLoaded = true
                    triggerBackgroundRefreshIfNeeded()
                }
                .onFailure {
                    if (!isEventsLoaded) {
                        errorMessage = it.message ?: "Error al cargar la aplicacion"
                    }
                }
        }
    }

    private fun updateStateFromCatalog(catalog: HomeCatalog) {
        searchableItems = catalog.searchableItems
        CatalogMemory.searchableItems = searchableItems
        channelLineup = searchableItems.filter { it.kind == ContentKind.CHANNEL }
        homeSections = buildDisplaySections(catalog.sections, searchableItems)

        if (selectedHero == null || searchableItems.none { it.stableId == selectedHero?.stableId }) {
            selectedHero = defaultItemForMode(currentMode)
        }
    }

    private fun triggerBackgroundRefreshIfNeeded() {
        if (isRefreshingInBackground || !repository.shouldRefreshPlaylistInBackground()) return

        scope.launch {
            isRefreshingInBackground = true
            runCatching {
                repository.refreshPlaylistInBackground()
                val catalog = repository.loadHomeCatalog(forceRefreshPlaylist = false)
                homeCatalog = catalog
                updateStateFromCatalog(catalog)
            }
            isRefreshingInBackground = false
        }
    }

    @Composable
    private fun ComposeRoot() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(IptvBackground),
        ) {
            when {
                !isSignedIn -> LoginScreen()
                errorMessage != null -> ErrorScreen(errorMessage.orEmpty())
                !isEventsLoaded && !isFullLoaded -> LoadingScreen()
                else -> MainShell()
            }

            if (isRefreshingInBackground) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .background(IptvSurfaceVariant, RoundedCornerShape(8.dp))
                        .border(1.dp, IptvFocusBorder.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        playlistProgress?.let(::formatProgressLabel) ?: "Actualizando guia...",
                        color = IptvTextPrimary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }

    @Composable
    private fun LoginScreen() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .background(IptvSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(10.dp))
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Iniciar sesion", color = IptvTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Introduce tu usuario y contrasena para descargar la playlist y cargar los canales.",
                    color = IptvTextMuted,
                    fontSize = 16.sp,
                )
                LoginField(
                    value = loginUsername,
                    label = "Usuario",
                    hidden = false,
                    onValueChange = { loginUsername = it },
                )
                LoginField(
                    value = loginPassword,
                    label = "Contrasena",
                    hidden = true,
                    onValueChange = { loginPassword = it },
                )
                loginError?.let {
                    Text(it, color = IptvLive, fontSize = 14.sp)
                }
                FocusButton(
                    label = if (isSigningIn) "Entrando..." else "Entrar",
                    icon = Icons.Outlined.PlayArrow,
                ) {
                    if (!isSigningIn) performSignIn()
                }
            }
        }
    }

    @Composable
    private fun LoginField(
        value: String,
        label: String,
        hidden: Boolean,
        onValueChange: (String) -> Unit,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = IptvTextMuted, fontSize = 14.sp)
            BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .background(IptvCard, RoundedCornerShape(8.dp))
                .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontSize = 16.sp,
            ),
            decorationBox = { innerTextField ->
                if (value.isBlank()) {
                    Text(
                        if (hidden) "Escribe tu contrasena" else "Escribe tu usuario",
                        color = IptvTextMuted,
                        fontSize = 16.sp,
                    )
                }
                innerTextField()
            },
        )
        }
    }

    private fun performSignIn() {
        loginError = null
        isSigningIn = true
        playlistProgress = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.signIn(loginUsername, loginPassword)
                }
            }
                .onSuccess {
                    resetCatalogState()
                    isSignedIn = true
                    isSigningIn = false
                    startProgressiveLoad()
                }
                .onFailure {
                    isSigningIn = false
                    Log.e("ComposeMainFragment", "Error al iniciar sesion", it)
                    loginError = it.message ?: "No se pudo iniciar sesion"
                }
        }
    }

    private fun performSignOut() {
        repository.signOut()
        resetCatalogState()
        isSignedIn = false
        loginUsername = ""
        loginPassword = ""
        loginError = null
        playlistProgress = null
    }

    private fun resetCatalogState() {
        homeCatalog = null
        homeSections = emptyList()
        searchableItems = emptyList()
        channelLineup = emptyList()
        selectedHero = null
        isEventsLoaded = false
        isFullLoaded = false
        errorMessage = null
        isRefreshingInBackground = false
        currentItem = null
        currentStreamIndex = 0
        currentMode = MainMode.Home
        selectedGroup = ALL_CHANNELS_GROUP
    }

    @Composable
    private fun LoadingScreen() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.width(520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("WalacTV", color = IptvTextPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(
                    playlistProgress?.let(::formatProgressLabel) ?: "Cargando catalogo y canales...",
                    color = IptvTextMuted,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(18.dp))
                ProgressBar(playlistProgress?.percent)
                Spacer(Modifier.height(10.dp))
                playlistProgress?.detail?.let { detail ->
                    Text(detail, color = IptvTextPrimary, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                }
                playlistProgress?.elapsedMs?.let { elapsedMs ->
                    Text(
                        "Tiempo transcurrido: ${formatElapsedMillis(elapsedMs)}",
                        color = IptvTextPrimary,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    when (playlistProgress?.stage) {
                        PlaylistLoadStage.DOWNLOADING -> "Descarga de la lista en curso"
                        PlaylistLoadStage.PARSING_FULL -> "Cargando lista completa de canales y VOD"
                        PlaylistLoadStage.SAVING_CACHE -> "Guardando cache para el siguiente arranque"
                        PlaylistLoadStage.READY -> "Lista lista"
                        else -> "Leyendo cache local"
                    },
                    color = IptvTextMuted,
                    fontSize = 14.sp,
                )
            }
        }
    }

    @Composable
    private fun ProgressBar(percent: Int?) {
        val fraction = ((percent ?: 0).coerceIn(0, 100)) / 100f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(IptvSurfaceVariant, RoundedCornerShape(6.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.04f))
                    .height(10.dp)
                    .background(IptvAccent, RoundedCornerShape(6.dp)),
            )
        }
    }

    private fun formatProgressLabel(progress: PlaylistLoadProgress): String {
        val suffix = progress.percent?.let { " $it%" }.orEmpty()
        return progress.message + suffix
    }

    private fun formatElapsedMillis(value: Long): String {
        val seconds = value / 1000f
        return if (seconds >= 60f) {
            String.format(Locale.US, "%.1f min", seconds / 60f)
        } else {
            String.format(Locale.US, "%.1f s", seconds)
        }
    }

    @Composable
    private fun ErrorScreen(message: String) {
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
                FocusButton(label = "Reintentar", icon = Icons.Outlined.PlayArrow) {
                    startProgressiveLoad()
                }
            }
        }
    }

    @Composable
    private fun MainShell() {
        Row(modifier = Modifier.fillMaxSize()) {
            SideRail()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                when (currentMode) {
                    MainMode.Home -> HomeContent()
                    MainMode.TV -> GuideContent(ContentKind.CHANNEL)
                    MainMode.Events -> GuideContent(ContentKind.EVENT)
                    MainMode.Movies -> VodGridContent(ContentKind.MOVIE)
                    MainMode.Series -> VodGridContent(ContentKind.SERIES)
                    MainMode.Settings -> SettingsContent()
                }
            }
        }
    }

    @Composable
    private fun SideRail() {
        val railItems = listOf(
            NavItem(Icons.Outlined.Search, "Buscar", null, false, ::openSearch),
            NavItem(Icons.Outlined.Home, "Inicio", MainMode.Home),
            NavItem(Icons.Outlined.Event, "Eventos", MainMode.Events),
            NavItem(Icons.Outlined.LiveTv, "TV en directo", MainMode.TV),
            NavItem(Icons.Outlined.Movie, "Peliculas", MainMode.Movies),
            NavItem(Icons.Outlined.Tv, "Series", MainMode.Series),
        )

        val railWidth by animateDpAsState(
            targetValue = if (isRailExpanded) 248.dp else 78.dp,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "railWidthAnim"
        )

        Column(
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(IptvSurface)
                .border(1.dp, IptvSurfaceVariant)
                .onFocusChanged { state ->
                    isRailExpanded = state.hasFocus
                },
        ) {
            Box(modifier = Modifier.height(100.dp)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isRailExpanded,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(150))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                        Text("WalacTV", color = IptvTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text("Navegacion", color = IptvTextMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                railItems.forEach { item ->
                    NavigationItem(
                        icon = item.icon,
                        label = item.label,
                        selected = item.mode != null && currentMode == item.mode,
                        expanded = isRailExpanded,
                    ) {
                        item.onClick?.invoke() ?: item.mode?.let(::changeMode)
                    }
                }
            }

            Box(modifier = Modifier.padding(10.dp)) {
                NavigationItem(
                    icon = Icons.Outlined.Settings,
                    label = "Ajustes",
                    selected = currentMode == MainMode.Settings,
                    expanded = isRailExpanded,
                ) {
                    changeMode(MainMode.Settings)
                }
            }
        }
    }

    private fun changeMode(newMode: MainMode) {
        if (currentMode == newMode) return
        currentMode = newMode
        selectedGroup = ALL_CHANNELS_GROUP
        selectedHero = defaultItemForMode(newMode)
    }

    private fun defaultItemForMode(mode: MainMode): CatalogItem? {
        return when (mode) {
            MainMode.Home -> homeSections.firstNotNullOfOrNull { it.items.firstOrNull() }
            MainMode.TV -> searchableItems.firstOrNull { it.kind == ContentKind.CHANNEL }
            MainMode.Events -> searchableItems.firstOrNull { it.kind == ContentKind.EVENT }
            MainMode.Movies -> searchableItems.firstOrNull { it.kind == ContentKind.MOVIE }
            MainMode.Series -> searchableItems.firstOrNull { it.kind == ContentKind.SERIES }
            MainMode.Settings -> null
        }
    }

    private fun openSearch() {
        isRailExpanded = false
        val searchFragment = SearchFragment().apply {
            setSearchData(searchableItems)
        }
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, searchFragment)
            .addToBackStack("SearchFragment")
            .commit()
    }

    @Composable
    private fun NavigationItem(
        icon: ImageVector,
        label: String,
        selected: Boolean,
        expanded: Boolean,
        onClick: () -> Unit,
    ) {
        var isFocused by remember { mutableStateOf(false) }
        val backgroundColor = when {
            isFocused -> IptvFocusBg
            selected -> IptvCard
            else -> Color.Transparent
        }
        val borderColor = when {
            isFocused -> IptvFocusBorder
            selected -> IptvSurfaceVariant
            else -> Color.Transparent
        }
        val contentColor = if (isFocused || selected) IptvTextPrimary else IptvTextMuted

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(backgroundColor, RoundedCornerShape(8.dp))
                .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .focusable()
                .onFocusChanged {
                    isFocused = it.isFocused
                }
                .padding(horizontal = if (expanded) 14.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(20.dp))
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(150))
            ) {
                Row {
                    Spacer(Modifier.width(14.dp))
                    Text(label, color = contentColor, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    @Composable
    private fun HomeContent() {
        val previewItem = selectedHero ?: homeSections.firstNotNullOfOrNull { it.items.firstOrNull() }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item {
                ScreenHeader(
                    title = "Inicio",
                    subtitle = "Acceso rapido a TV, eventos y ultimos canales",
                )
            }

            previewItem?.let { item ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        DetailPanel(
                            item = item,
                            modifier = Modifier.weight(1.1f),
                            primaryActionLabel = "Reproducir",
                        ) {
                            handleCardClick(item)
                        }
                        HomeSummaryPanel(modifier = Modifier.weight(0.9f))
                    }
                }
            }

            items(homeSections) { section ->
                ContentSection(section = section) { focusedItem ->
                    selectedHero = focusedItem
                }
            }
        }
    }

    @Composable
    private fun HomeSummaryPanel(modifier: Modifier = Modifier) {
        val favoriteCount = channelStateStore.favoriteIds().size
        val recentCount = channelStateStore.recentIds().size
        val eventCount = searchableItems.count { it.kind == ContentKind.EVENT }
        val movieCount = searchableItems.count { it.kind == ContentKind.MOVIE }

        Column(
            modifier = modifier
                .background(IptvSurface, RoundedCornerShape(10.dp))
                .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(10.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Resumen de biblioteca", color = IptvTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            SimpleMetricRow("Favoritos", favoriteCount.toString(), Icons.Outlined.FavoriteBorder)
            SimpleMetricRow("Ultimos canales", recentCount.toString(), Icons.Outlined.History)
            SimpleMetricRow("Eventos", eventCount.toString(), Icons.Outlined.Event)
            SimpleMetricRow("Peliculas", movieCount.toString(), Icons.Outlined.Movie)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pulsa izquierda para navegar entre secciones y arriba para volver al menu lateral.",
                color = IptvTextMuted,
                fontSize = 15.sp,
            )
        }
    }

    @Composable
    private fun SimpleMetricRow(label: String, value: String, icon: ImageVector) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = IptvAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = IptvTextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(value, color = IptvTextMuted, fontSize = 16.sp)
        }
    }

    @Composable
    private fun ContentSection(section: BrowseSection, onFocused: (CatalogItem) -> Unit) {
        val lazyListState = rememberLazyListState()
        
        LaunchedEffect(section.items) {
            if (section.items.firstOrNull()?.kind == ContentKind.EVENT) {
                val index = section.items.indexOfFirst { isLikelyLiveNow(it) }
                if (index > 0) {
                    lazyListState.scrollToItem(index)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(section.title, color = IptvTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Text(countLabel(section.items.size), color = IptvTextMuted, fontSize = 14.sp)
            }
            LazyRow(state = lazyListState, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(section.items) { item ->
                    MediaCard(item = item, onFocused = { onFocused(item) }) {
                        handleCardClick(item)
                    }
                }
            }
        }
    }

    @Composable
    private fun MediaCard(
        item: CatalogItem,
        onFocused: () -> Unit,
        onClick: () -> Unit,
    ) {
        var isFocused by remember { mutableStateOf(false) }
        val cardWidth = when (item.kind) {
            ContentKind.CHANNEL -> 180.dp
            ContentKind.EVENT -> 220.dp
            else -> 140.dp
        }
        val imageHeight = when (item.kind) {
            ContentKind.CHANNEL -> 100.dp
            ContentKind.EVENT -> 120.dp
            else -> 200.dp
        }

        Column(
            modifier = Modifier
                .width(cardWidth)
                .background(if (isFocused) IptvFocusBg else IptvCard, RoundedCornerShape(10.dp))
                .border(1.dp, if (isFocused) IptvFocusBorder else IptvSurfaceVariant, RoundedCornerShape(10.dp))
                .clickable { onClick() }
                .focusable()
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) onFocused()
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .background(IptvSurfaceVariant, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.kind == ContentKind.EVENT) {
                    EventSportPlaceholder(item)
                } else if (item.imageUrl.isNotBlank()) {
                    RemoteImage(
                        url = item.imageUrl,
                        width = 600,
                        height = 800,
                        scaleType = if (item.kind == ContentKind.CHANNEL) ScaleType.FIT_CENTER else ScaleType.CENTER_CROP,
                    )
                } else {
                    PlaceholderIcon(kind = item.kind)
                }

                item.badgeText.takeIf { it.isNotBlank() }?.let { badge ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .background(if (item.kind == ContentKind.EVENT) IptvLive else IptvSurface, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(badge, color = IptvTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = buildCardTitle(item),
                    color = IptvTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.subtitle.ifBlank { item.group.ifBlank { kindLabel(item.kind) } },
                    color = IptvTextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    private fun buildCardTitle(item: CatalogItem): String {
        return if (item.channelNumber != null) {
            "${item.channelNumber}  ${item.title}"
        } else {
            item.title
        }
    }

    @Composable
    private fun GuideContent(kind: ContentKind) {
        val sourceItems = remember(searchableItems, kind) {
            searchableItems.filter { it.kind == kind }
        }
        val groups = remember(sourceItems, kind) {
            buildList {
                add(ALL_CHANNELS_GROUP)
                if (kind == ContentKind.CHANNEL) add(FAVORITES_GROUP)
                sourceItems.map(CatalogItem::group)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                    .forEach(::add)
            }
        }
        val displayItems = remember(selectedGroup, sourceItems, kind) {
            when (selectedGroup) {
                ALL_CHANNELS_GROUP -> sourceItems
                FAVORITES_GROUP -> {
                    val favoriteIds = channelStateStore.favoriteIds()
                    sourceItems.filter { favoriteIds.contains(it.stableId) }
                }
                else -> sourceItems.filter { it.group == selectedGroup }
            }
        }
        val previewItem = selectedHero?.takeIf { displayItems.any { candidate -> candidate.stableId == it.stableId } }
            ?: displayItems.firstOrNull()

        val lazyListState = rememberLazyListState()
        
        LaunchedEffect(displayItems) {
            if (kind == ContentKind.EVENT) {
                val index = displayItems.indexOfFirst { isLikelyLiveNow(it) }
                if (index > 0) {
                    lazyListState.scrollToItem(index)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ScreenHeader(
                title = screenTitle(kind),
                subtitle = "Filtra por grupo y entra directo con el mando",
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                FilterColumn(
                    groups = groups,
                    selected = selectedGroup,
                    modifier = Modifier.width(248.dp),
                ) {
                    selectedGroup = it
                    selectedHero = null
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.width(if (kind == ContentKind.CHANNEL) 430.dp else 500.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(displayItems) { item ->
                        GuideRow(item = item, onFocused = { selectedHero = item }) {
                            handleCardClick(item)
                        }
                    }
                }

                previewItem?.let { item ->
                    DetailPanel(
                        item = item,
                        modifier = Modifier.weight(1f),
                        primaryActionLabel = if (kind == ContentKind.CHANNEL) "Ver canal" else "Abrir",
                    ) {
                        handleCardClick(item)
                    }
                }
            }
        }
    }

    @Composable
    private fun VodGridContent(kind: ContentKind) {
        val sourceItems = remember(searchableItems, kind) {
            searchableItems.filter { it.kind == kind }
        }
        val groups = remember(sourceItems) {
            buildList {
                add(ALL_CHANNELS_GROUP)
                sourceItems.map(CatalogItem::group)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                    .forEach(::add)
            }
        }
        val displayItems = remember(selectedGroup, sourceItems) {
            when (selectedGroup) {
                ALL_CHANNELS_GROUP -> sourceItems
                else -> sourceItems.filter { it.group == selectedGroup }
            }
        }
        val previewItem = selectedHero?.takeIf { displayItems.any { candidate -> candidate.stableId == it.stableId } }
            ?: displayItems.firstOrNull()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ScreenHeader(
                title = screenTitle(kind),
                subtitle = "Catalogo visual con grupos simples y foco claro",
            )

            previewItem?.let { item ->
                DetailPanel(item = item, modifier = Modifier.fillMaxWidth(), primaryActionLabel = "Reproducir") {
                    handleCardClick(item)
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                FilterColumn(
                    groups = groups,
                    selected = selectedGroup,
                    modifier = Modifier.width(248.dp),
                ) {
                    selectedGroup = it
                    selectedHero = null
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(190.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    gridItems(displayItems) { item ->
                        MediaCard(item = item, onFocused = { selectedHero = item }) {
                            handleCardClick(item)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FilterColumn(
        groups: List<String>,
        selected: String,
        modifier: Modifier = Modifier,
        onSelected: (String) -> Unit,
    ) {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .background(IptvSurface, RoundedCornerShape(10.dp))
                .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Grupos", color = IptvTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups) { group ->
                    FilterItem(label = group, selected = group == selected) {
                        onSelected(group)
                    }
                }
            }
        }
    }

    @Composable
    private fun FilterItem(label: String, selected: Boolean, onClick: () -> Unit) {
        var isFocused by remember { mutableStateOf(false) }
        val backgroundColor = when {
            isFocused -> IptvFocusBg
            selected -> IptvCard
            else -> Color.Transparent
        }
        val borderColor = when {
            isFocused -> IptvFocusBorder
            selected -> IptvSurfaceVariant
            else -> Color.Transparent
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor, RoundedCornerShape(8.dp))
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .focusable()
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) onClick()
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                label,
                color = if (isFocused || selected) IptvTextPrimary else IptvTextMuted,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    private fun GuideRow(
        item: CatalogItem,
        onFocused: () -> Unit,
        onClick: () -> Unit,
    ) {
        var isFocused by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isFocused) IptvFocusBg else IptvCard, RoundedCornerShape(10.dp))
                .border(1.dp, if (isFocused) IptvFocusBorder else IptvSurfaceVariant, RoundedCornerShape(10.dp))
                .clickable { onClick() }
                .focusable()
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.channelNumber?.toString().orEmpty(),
                color = if (isFocused) IptvTextPrimary else IptvTextMuted,
                fontSize = 16.sp,
                modifier = Modifier.width(48.dp),
            )
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(IptvSurfaceVariant, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.kind == ContentKind.EVENT) {
                    EventSportPlaceholder(item, emojiSize = 28.sp)
                } else if (item.imageUrl.isNotBlank()) {
                    RemoteImage(item.imageUrl, 160, 160, ScaleType.FIT_CENTER)
                } else {
                    PlaceholderIcon(kind = item.kind, size = 22.dp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, color = IptvTextPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    item.subtitle.ifBlank { item.group.ifBlank { kindLabel(item.kind) } },
                    color = IptvTextMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item.badgeText.takeIf { it.isNotBlank() }?.let { badge ->
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .background(IptvSurface, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(badge, color = IptvTextMuted, fontSize = 12.sp)
                }
            }
        }
    }

    @Composable
    private fun DetailPanel(
        item: CatalogItem,
        modifier: Modifier = Modifier,
        primaryActionLabel: String,
        onPrimaryAction: () -> Unit,
    ) {
        Column(
            modifier = modifier
                .background(IptvSurface, RoundedCornerShape(10.dp))
                .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(10.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(
                    modifier = Modifier
                        .width(if (item.kind == ContentKind.CHANNEL) 220.dp else 260.dp)
                        .height(if (item.kind == ContentKind.CHANNEL) 140.dp else 320.dp)
                        .background(IptvSurfaceVariant, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.kind == ContentKind.EVENT) {
                        EventSportPlaceholder(item, emojiSize = 72.sp)
                    } else if (item.imageUrl.isNotBlank()) {
                        RemoteImage(
                            url = item.imageUrl,
                            width = 720,
                            height = 960,
                            scaleType = if (item.kind == ContentKind.CHANNEL) ScaleType.FIT_CENTER else ScaleType.CENTER_CROP,
                        )
                    } else {
                        PlaceholderIcon(kind = item.kind, size = 48.dp)
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(item.title, color = IptvTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(item.group.ifBlank { null }, item.subtitle.ifBlank { null }, kindLabel(item.kind)).filterNotNull().joinToString("  •  "),
                        color = IptvTextMuted,
                        fontSize = 16.sp,
                    )
                    item.badgeText.takeIf { it.isNotBlank() }?.let { badge ->
                        Box(
                            modifier = Modifier
                                .background(if (item.kind == ContentKind.EVENT) IptvLive else IptvCard, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(badge, color = IptvTextPrimary, fontSize = 12.sp)
                        }
                    }
                    Text(
                        item.description.ifBlank { "Listo para reproducir" },
                        color = IptvTextPrimary,
                        fontSize = 16.sp,
                        maxLines = 7,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    FocusButton(label = primaryActionLabel, icon = Icons.Outlined.PlayArrow, onClick = onPrimaryAction)
                }
            }
        }
    }

    @Composable
    private fun FocusButton(label: String, icon: ImageVector, onClick: () -> Unit) {
        var isFocused by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .height(52.dp)
                .background(if (isFocused) IptvAccent else IptvCard, RoundedCornerShape(8.dp))
                .border(1.dp, if (isFocused) IptvTextPrimary else IptvSurfaceVariant, RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .focusable()
                .onFocusChanged { isFocused = it.isFocused }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = label, tint = IptvTextPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = IptvTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }

    @Composable
    private fun PlaceholderIcon(kind: ContentKind, size: androidx.compose.ui.unit.Dp = 32.dp) {
        val icon = when (kind) {
            ContentKind.EVENT -> Icons.Outlined.Event
            ContentKind.CHANNEL -> Icons.Outlined.LiveTv
            ContentKind.MOVIE -> Icons.Outlined.Movie
            ContentKind.SERIES -> Icons.Outlined.Tv
        }
        Icon(icon, contentDescription = null, tint = IptvTextMuted, modifier = Modifier.size(size))
    }

    @Composable
    private fun EventSportPlaceholder(item: CatalogItem, emojiSize: TextUnit = 48.sp) {
        val rawText = item.title.lowercase() + " " + item.group.lowercase()
        val text = java.text.Normalizer.normalize(rawText, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            
        val emoji = when {
            text.contains("fut") || text.contains("champ") || text.contains("liga") || text.contains("uefa") || text.contains("soccer") || text.contains("madrid") || text.contains("barca") || text.contains("barcelona") || text.contains("atletico") -> "⚽"
            text.contains("basket") || text.contains("nba") || text.contains("lakers") || text.contains("warriors") || text.contains("celtics") -> "🏀"
            text.contains("tenis") || text.contains("atp") || text.contains("wta") || text.contains("grand slam") || text.contains("wimbledon") || text.contains("nadal") || text.contains("alcaraz") || text.contains("djokovic") -> "🎾"
            text.contains("motor") || text.contains("f1") || text.contains("moto") || text.contains("nascar") || text.contains("rally") || text.contains("dakar") -> "🏎️"
            text.contains("mma") || text.contains("ufc") || text.contains("box") || text.contains("bellator") -> "🥊"
            text.contains("nfl") || text.contains("rugby") || text.contains("americano") || text.contains("super bowl") -> "🏈"
            text.contains("beisbol") || text.contains("mlb") -> "⚾"
            text.contains("golf") || text.contains("pga") -> "⛳"
            text.contains("ciclismo") || text.contains("tour") || text.contains("vuelta") || text.contains("giro") -> "🚴"
            text.contains("juegos") || text.contains("olimpi") -> "🏅"
            text.contains("esport") || text.contains("gaming") || text.contains("lol") || text.contains("valorant") || text.contains("csgo") -> "🎮"
            else -> "🏆"
        }
        
        val colors = when {
            text.contains("fut") || text.contains("champ") || text.contains("liga") || text.contains("madrid") || text.contains("barca") -> listOf(Color(0xFF0B6E4F), Color(0xFF1A936F))
            text.contains("tenis") || text.contains("wta") || text.contains("atp") -> listOf(Color(0xFF254441), Color(0xFF43AA8B))
            text.contains("mma") || text.contains("box") || text.contains("ufc") -> listOf(Color(0xFF5F0F40), Color(0xFF9A031E))
            text.contains("basket") || text.contains("nba") -> listOf(Color(0xFF7F4F24), Color(0xFFD68C45))
            text.contains("motor") || text.contains("f1") || text.contains("moto") -> listOf(Color(0xFF1D3557), Color(0xFF457B9D))
            text.contains("esport") || text.contains("gaming") -> listOf(Color(0xFF3B1E54), Color(0xFF9B59B6))
            else -> listOf(Color(0xFF102A43), Color(0xFFD64550))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = emojiSize,
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    private fun ScreenHeader(title: String, subtitle: String) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = IptvTextPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = IptvTextMuted, fontSize = 16.sp)
        }
    }

    @Composable
    private fun SettingsContent() {
        val cacheExists = repository.hasPlaylistCache()
        val cacheSize = formatBytes(repository.getPlaylistCacheSizeBytes())
        val isCatalogReady = if (isFullLoaded) "Cargada" else "Cargando"

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ScreenHeader(
                title = "Ajustes",
                subtitle = "Control de cache, ultima actualizacion y recarga manual",
            )

            Column(
                modifier = Modifier
                    .width(760.dp)
                    .background(IptvSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(10.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SettingsRow("Estado de la lista", isCatalogReady)
                SettingsRow("Ultima actualizacion", formatElapsed(repository.getLastPlaylistUpdateMillis()))
                SettingsRow("Cache local", if (cacheExists) "Disponible ($cacheSize)" else "No disponible")
                SettingsRow("Canales cargados", channelLineup.size.toString())
                SettingsRow("Contenido indexado", searchableItems.size.toString())
                Text(
                    "La lista M3U se guarda en local y solo se vuelve a descargar cuando hace falta o cuando la fuerzas desde aqui.",
                    color = IptvTextMuted,
                    fontSize = 15.sp,
                )
                FocusButton(label = "Refrescar lista ahora", icon = Icons.Outlined.History) {
                    refreshPlaylist()
                }
                FocusButton(label = "Cerrar sesion", icon = Icons.Outlined.Settings) {
                    performSignOut()
                }
            }
        }
    }

    @Composable
    private fun SettingsRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = IptvTextPrimary, fontSize = 16.sp)
            Text(value, color = IptvTextMuted, fontSize = 16.sp)
        }
    }

    private fun handleCardClick(item: CatalogItem) {
        playCatalogItem(item, 0)
    }

    private fun refreshPlaylist() {
        homeCatalog = null
        homeSections = emptyList()
        searchableItems = emptyList()
        channelLineup = emptyList()
        selectedHero = null
        isEventsLoaded = false
        isFullLoaded = false
        repository.clearHomeMemoryCache()
        loadFullPlaylist(forceRefresh = true)
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun playCatalogItem(item: CatalogItem, optionIndex: Int) {
        if (item.kind == ContentKind.EVENT) {
            scope.launch {
                val resolvedItem = repository.resolveEventItem(item)
                if (resolvedItem.streamOptions.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.no_streams_available, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                playResolvedCatalogItem(resolvedItem, optionIndex.coerceIn(resolvedItem.streamOptions.indices))
            }
            return
        }

        playResolvedCatalogItem(item, optionIndex)
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun playResolvedCatalogItem(item: CatalogItem, optionIndex: Int) {
        val stream = item.streamOptions.getOrNull(optionIndex) ?: return
        currentItem = item
        currentStreamIndex = optionIndex

        if (item.kind == ContentKind.CHANNEL) {
            channelStateStore.markRecent(item)
        }

        val fragmentManager = requireActivity().supportFragmentManager
        fragmentManager.findFragmentById(R.id.player_container)?.let { existing ->
            fragmentManager.beginTransaction().remove(existing).commitNow()
        }

        val playerFragment = PlayerFragment(
            streamUrl = stream.url,
            overlayNumber = when {
                item.kind == ContentKind.CHANNEL && item.channelNumber != null -> "CH ${item.channelNumber}"
                item.kind == ContentKind.EVENT -> "EN DIRECTO"
                else -> item.kind.name
            },
            overlayTitle = item.title,
            overlayMeta = listOf(item.subtitle, stream.label).filter { it.isNotBlank() }.joinToString("  •  ").ifBlank { item.description },
            onNavigateChannel = ::navigateChannel,
            onNavigateOption = ::navigateOption,
            onDirectChannelNumber = ::navigateToChannelNumber,
            onToggleFavorite = { toggleFavorite(item) },
            onOpenFavorites = ::openFavoriteChannel,
            onOpenRecents = ::openRecentChannel,
        )

        fragmentManager.beginTransaction()
            .replace(R.id.player_container, playerFragment, "player_fragment")
            .commitNow()

        requireActivity().findViewById<FrameLayout>(R.id.player_container).visibility = View.VISIBLE
    }

    private fun navigateChannel(direction: Int) {
        val current = currentItem ?: return
        if (current.kind != ContentKind.CHANNEL || channelLineup.isEmpty()) return
        val currentIndex = channelLineup.indexOfFirst { it.stableId == current.stableId }
        if (currentIndex == -1) return
        val targetIndex = currentIndex + direction
        if (targetIndex !in channelLineup.indices) return
        playCatalogItem(channelLineup[targetIndex], 0)
    }

    private fun navigateOption(direction: Int) {
        val item = currentItem ?: return
        val newIndex = currentStreamIndex + direction
        if (newIndex !in item.streamOptions.indices) return
        playCatalogItem(item, newIndex)
    }

    private fun navigateToChannelNumber(number: Int): Boolean {
        val match = channelLineup.firstOrNull { it.channelNumber == number } ?: return false
        playCatalogItem(match, 0)
        return true
    }

    private fun toggleFavorite(item: CatalogItem): Boolean {
        val result = channelStateStore.toggleFavorite(item)
        homeSections = buildDisplaySections(homeCatalog?.sections.orEmpty(), searchableItems)
        return result
    }

    private fun openFavoriteChannel(): Boolean {
        val favoriteIds = channelStateStore.favoriteIds()
        val match = channelLineup.firstOrNull { favoriteIds.contains(it.stableId) } ?: return false
        playCatalogItem(match, 0)
        return true
    }

    private fun openRecentChannel(): Boolean {
        val byId = channelLineup.associateBy(CatalogItem::stableId)
        val match = channelStateStore.recentIds().drop(1).mapNotNull(byId::get).firstOrNull()
            ?: channelStateStore.recentIds().mapNotNull(byId::get).firstOrNull()
            ?: return false
        playCatalogItem(match, 0)
        return true
    }

    @Composable
    private fun RemoteImage(
        url: String,
        width: Int,
        height: Int,
        scaleType: ScaleType,
    ) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    this.scaleType = scaleType
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                }
            },
            update = { imageView ->
                imageView.scaleType = scaleType
                Glide.with(imageView)
                    .load(url)
                    .override(width, height)
                    .dontTransform()
                    .into(imageView)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    private fun buildDisplaySections(baseSections: List<BrowseSection>, allItems: List<CatalogItem>): List<BrowseSection> {
        val eventSections = baseSections.filter { it.items.firstOrNull()?.kind == ContentKind.EVENT }
        val channels = allItems.filter { it.kind == ContentKind.CHANNEL }
        val movies = allItems.filter { it.kind == ContentKind.MOVIE }
        val series = allItems.filter { it.kind == ContentKind.SERIES }

        return buildList {
            eventSections.firstOrNull()?.let(::add)
            buildFavoriteSection(channels)?.let(::add)
            buildRecentSection(channels)?.let(::add)
            addAll(buildTypeSections("Canales", channels, 24))
            addAll(buildTypeSections("Peliculas", movies, 16))
            addAll(buildTypeSections("Series", series, 16))
        }
    }

    private fun buildFavoriteSection(channels: List<CatalogItem>): BrowseSection? {
        val favoriteIds = channelStateStore.favoriteIds()
        val items = channels.filter { favoriteIds.contains(it.stableId) }
        return items.takeIf { it.isNotEmpty() }?.let { BrowseSection("Favoritos", it) }
    }

    private fun buildRecentSection(channels: List<CatalogItem>): BrowseSection? {
        val byId = channels.associateBy(CatalogItem::stableId)
        val items = channelStateStore.recentIds().mapNotNull(byId::get)
        return items.takeIf { it.isNotEmpty() }?.let { BrowseSection("Ultimos canales", it) }
    }

    private fun buildTypeSections(title: String, items: List<CatalogItem>, mainLimit: Int): List<BrowseSection> {
        if (items.isEmpty()) return emptyList()
        return buildList {
            add(BrowseSection(title, items.take(mainLimit)))
            items.groupBy { it.group.ifBlank { "Sin categoria" } }
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, List<CatalogItem>>> { it.value.size }.thenBy { it.key })
                .take(3)
                .forEach { add(BrowseSection(it.key, it.value.take(14))) }
        }
    }

    private fun screenTitle(kind: ContentKind): String {
        return when (kind) {
            ContentKind.EVENT -> "Eventos"
            ContentKind.CHANNEL -> "TV en directo"
            ContentKind.MOVIE -> "Peliculas"
            ContentKind.SERIES -> "Series"
        }
    }

    private fun kindLabel(kind: ContentKind): String {
        return when (kind) {
            ContentKind.EVENT -> "Evento"
            ContentKind.CHANNEL -> "Canal"
            ContentKind.MOVIE -> "Pelicula"
            ContentKind.SERIES -> "Serie"
        }
    }

    private fun countLabel(count: Int): String {
        return if (count == 1) "1 elemento" else "$count elementos"
    }

    private fun formatElapsed(lastUpdated: Long): String {
        if (lastUpdated == 0L) return "Nunca"
        val elapsedMinutes = ((System.currentTimeMillis() - lastUpdated) / 60_000L).coerceAtLeast(1)
        return when {
            elapsedMinutes >= 60 * 24 -> "Hace ${elapsedMinutes / (60 * 24)} dias"
            elapsedMinutes >= 60 -> "Hace ${elapsedMinutes / 60} horas"
            else -> "Hace $elapsedMinutes minutos"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024f
        val mb = kb / 1024f
        return when {
            mb >= 1f -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1f -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        repository.setPlaylistProgressListener(null)
        scope.cancel()
    }

    private enum class MainMode {
        Home,
        TV,
        Movies,
        Series,
        Events,
        Settings,
    }

    private data class NavItem(
        val icon: ImageVector,
        val label: String,
        val mode: MainMode? = null,
        val activatesOnFocus: Boolean = true,
        val onClick: (() -> Unit)? = null,
    )
    
    private fun isLikelyLiveNow(item: CatalogItem): Boolean {
        if (item.kind != ContentKind.EVENT) return false
        val parsed = runCatching { EVENT_TIME_FORMAT.parse(item.badgeText) }.getOrNull() ?: return false
        val now = Calendar.getInstance()
        val eventCalendar = Calendar.getInstance().apply {
            time = parsed
            set(Calendar.YEAR, now.get(Calendar.YEAR))
            set(Calendar.MONTH, now.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
        }
        val deltaMinutes = (now.timeInMillis - eventCalendar.timeInMillis) / 60_000L
        return deltaMinutes in -20..180
    }

    companion object {
        private const val ALL_CHANNELS_GROUP = "Todos"
        private const val FAVORITES_GROUP = "Favoritos"
        private val EVENT_TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
    }
}
