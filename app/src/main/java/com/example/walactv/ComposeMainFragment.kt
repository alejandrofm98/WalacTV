@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.example.walactv

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import com.example.walactv.ui.theme.IptvOnline
import com.example.walactv.ui.theme.IptvSurface
import com.example.walactv.ui.theme.IptvSurfaceVariant
import com.example.walactv.ui.theme.IptvTextMuted
import com.example.walactv.ui.theme.IptvTextPrimary
import com.example.walactv.ui.theme.WalacTVTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ComposeMainFragment : Fragment() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: IptvRepository
    private lateinit var appUpdateRepository: AppUpdateRepository
    private lateinit var channelStateStore: ChannelStateStore

    private var homeCatalog by mutableStateOf<HomeCatalog?>(null)
    private var homeSections by mutableStateOf<List<BrowseSection>>(emptyList())
    private var searchableItems by mutableStateOf<List<CatalogItem>>(emptyList())
    private var channelLineup by mutableStateOf<List<CatalogItem>>(emptyList())
    private var channelFilters by mutableStateOf(CatalogFilters())
    private var movieFilters by mutableStateOf(CatalogFilters())
    private var seriesFilters by mutableStateOf(CatalogFilters())
    private var channelFilterCountry by mutableStateOf<String?>(null)
    private var movieFilterCountry by mutableStateOf<String?>(null)
    private var seriesFilterCountry by mutableStateOf<String?>(null)
    private var selectedHero by mutableStateOf<CatalogItem?>(null)
    private var currentMode by mutableStateOf(MainMode.Home)
    private var isRailExpanded by mutableStateOf(false)
    private var isSignedIn by mutableStateOf(false)
    private var loginUsername by mutableStateOf("")
    private var loginPassword by mutableStateOf("")
    private var loginError by mutableStateOf<String?>(null)
    private var isSigningIn by mutableStateOf(false)

    private var isLoaded by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var installedAppVersion by mutableStateOf<InstalledAppVersion?>(null)
    private var availableUpdate by mutableStateOf<AppUpdateInfo?>(null)
    private var mandatoryUpdate by mutableStateOf<AppUpdateInfo?>(null)
    private var updateStatusMessage by mutableStateOf("No comprobado")
    private var updateErrorMessage by mutableStateOf<String?>(null)
    private var isCheckingUpdates by mutableStateOf(false)
    private var isUpdateDownloading by mutableStateOf(false)
    private var pendingInstallPermission by mutableStateOf(false)

    private var currentItem: CatalogItem? = null
    private var currentStreamIndex: Int = 0
    private var activePlaybackLineup: List<CatalogItem> = emptyList()
    private var pendingUpdateDownloadId: Long? = null

    private val updateDownloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId == -1L || downloadId != pendingUpdateDownloadId) return
            handleCompletedUpdateDownload(downloadId)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        repository = IptvRepository(requireContext())
        appUpdateRepository = AppUpdateRepository(requireContext())
        channelStateStore = ChannelStateStore(requireContext())
        installedAppVersion = appUpdateRepository.installedVersion()
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
        restoreCachedUpdateState()
        checkForAppUpdates()
        if (isSignedIn) {
            startLoad()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().registerReceiver(updateDownloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            requireContext().registerReceiver(updateDownloadReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingInstallPermission && canRequestPackageInstalls()) {
            pendingInstallPermission = false
            startUpdateDownload(mandatoryUpdate ?: availableUpdate)
        }
    }

    override fun onStop() {
        runCatching { requireContext().unregisterReceiver(updateDownloadReceiver) }
        super.onStop()
    }

    private fun startLoad(forceRefresh: Boolean = false) {
        if (homeCatalog != null && !forceRefresh) return

        scope.launch {
            errorMessage = null
            runCatching { repository.loadHomeCatalog(forceRefresh = forceRefresh) }
                .onSuccess { catalog ->
                    homeCatalog = catalog
                    updateStateFromCatalog(catalog)
                    isLoaded = true
                }
                .onFailure {
                    if (!isLoaded) {
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

    private fun restoreCachedUpdateState() {
        val installed = installedAppVersion ?: return
        val cachedUpdate = appUpdateRepository.loadCachedUpdate() ?: return
        availableUpdate = cachedUpdate
        when (evaluateAppUpdate(installed, cachedUpdate)) {
            AppUpdateAvailability.REQUIRED -> {
                mandatoryUpdate = cachedUpdate
                updateStatusMessage = "Actualizacion obligatoria disponible"
            }
            AppUpdateAvailability.OPTIONAL -> {
                mandatoryUpdate = null
                updateStatusMessage = "Nueva version ${cachedUpdate.latestVersionName} disponible"
            }
            AppUpdateAvailability.UP_TO_DATE -> {
                mandatoryUpdate = null
                updateStatusMessage = "Aplicacion actualizada"
            }
        }
    }

    private fun checkForAppUpdates(showToast: Boolean = false) {
        scope.launch {
            isCheckingUpdates = true
            updateErrorMessage = null

            val result = runCatching { appUpdateRepository.fetchRemoteUpdate() }
            if (result.isFailure) {
                Log.e(TAG, "Error al comprobar actualizaciones", result.exceptionOrNull())
            }
            val remoteUpdate = result.getOrNull()
            if (remoteUpdate == null) {
                if (mandatoryUpdate != null) {
                    updateStatusMessage = "Actualizacion obligatoria pendiente"
                } else {
                    updateStatusMessage = "No se pudo comprobar"
                    if (showToast) {
                        Toast.makeText(requireContext(), "No se pudo comprobar: ${result.exceptionOrNull()?.message ?: "sin conexion"}", Toast.LENGTH_LONG).show()
                    }
                }
                isCheckingUpdates = false
                return@launch
            }

            appUpdateRepository.cacheUpdate(remoteUpdate)
            availableUpdate = remoteUpdate

            when (evaluateAppUpdate(installedAppVersion ?: return@launch, remoteUpdate)) {
                AppUpdateAvailability.REQUIRED -> {
                    mandatoryUpdate = remoteUpdate
                    updateStatusMessage = "Actualizacion obligatoria disponible"
                }
                AppUpdateAvailability.OPTIONAL -> {
                    mandatoryUpdate = null
                    updateStatusMessage = "Nueva version ${remoteUpdate.latestVersionName} disponible"
                }
                AppUpdateAvailability.UP_TO_DATE -> {
                    mandatoryUpdate = null
                    updateStatusMessage = "Aplicacion actualizada"
                }
            }

            if (showToast) {
                Toast.makeText(requireContext(), updateStatusMessage, Toast.LENGTH_SHORT).show()
            }
            isCheckingUpdates = false
        }
    }

    private fun startUpdateFlow() {
        val updateInfo = mandatoryUpdate ?: availableUpdate ?: return
        if (!canRequestPackageInstalls()) {
            pendingInstallPermission = true
            updateStatusMessage = "Permite instalar apps desconocidas para continuar"
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${requireContext().packageName}"),
            )
            val canOpenSettings = intent.resolveActivity(requireContext().packageManager) != null
            if (canOpenSettings) {
                runCatching { startActivity(intent) }
                    .onFailure { updateErrorMessage = "No se pudo abrir la configuracion de instalacion" }
            } else {
                updateErrorMessage = "Activa manualmente la instalacion desde origenes desconocidos para WalacTV"
            }
            return
        }
        startUpdateDownload(updateInfo)
    }

    private fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                requireContext().packageManager.canRequestPackageInstalls()

    private fun startUpdateDownload(updateInfo: AppUpdateInfo?) {
        val info = updateInfo ?: return
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("WalacTV ${info.latestVersionName}")
            .setDescription("Descargando actualizacion")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                requireContext(),
                Environment.DIRECTORY_DOWNLOADS,
                "WalacTV-${info.latestVersionName}.apk",
            )

        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        pendingUpdateDownloadId = downloadManager.enqueue(request)
        isUpdateDownloading = true
        updateStatusMessage = "Descargando actualizacion ${info.latestVersionName}"
        Toast.makeText(requireContext(), updateStatusMessage, Toast.LENGTH_SHORT).show()
    }

    private fun handleCompletedUpdateDownload(downloadId: Long) {
        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    isUpdateDownloading = false
                    pendingUpdateDownloadId = null
                    val uri = downloadManager.getUriForDownloadedFile(downloadId)
                    if (uri != null) launchApkInstaller(uri)
                    else updateErrorMessage = "La descarga termino pero no se pudo abrir el APK"
                }
                DownloadManager.STATUS_FAILED -> {
                    isUpdateDownloading = false
                    pendingUpdateDownloadId = null
                    updateErrorMessage = "No se pudo descargar la actualizacion"
                    updateStatusMessage = "Error al descargar la actualizacion"
                }
            }
        }
    }

    private fun launchApkInstaller(apkUri: Uri) {
        val contentUri = try {
            val file = java.io.File(apkUri.path ?: return)
            androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file,
            )
        } catch (_: Exception) {
            apkUri
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure { updateErrorMessage = "No se pudo abrir el instalador" }
    }

    // ── Compose root ──────────────────────────────────────────────────────────

    @Composable
    private fun ComposeRoot() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(IptvBackground),
        ) {
            if (mandatoryUpdate != null) {
                MandatoryUpdateScreen(mandatoryUpdate!!)
            } else {
                when {
                    !isSignedIn -> LoginScreen()
                    errorMessage != null -> ErrorScreen(errorMessage.orEmpty())
                    !isLoaded -> LoadingScreen()
                    else -> MainShell()
                }
            }
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

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
                    "Introduce tu usuario y contrasena para cargar los canales.",
                    color = IptvTextMuted,
                    fontSize = 16.sp,
                )
                LoginField(value = loginUsername, label = "Usuario", hidden = false) { loginUsername = it }
                LoginField(value = loginPassword, label = "Contrasena", hidden = true) { loginPassword = it }
                loginError?.let { Text(it, color = IptvLive, fontSize = 14.sp) }
                FocusButton(label = if (isSigningIn) "Entrando..." else "Entrar", icon = Icons.Outlined.PlayArrow) {
                    if (!isSigningIn) performSignIn()
                }
            }
        }
    }

    @Composable
    private fun LoginField(value: String, label: String, hidden: Boolean, onValueChange: (String) -> Unit) {
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
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
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
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.signIn(loginUsername, loginPassword) } }
                .onSuccess {
                    resetCatalogState()
                    isSignedIn = true
                    isSigningIn = false
                    startLoad()
                }
                .onFailure {
                    isSigningIn = false
                    Log.e(TAG, "Error al iniciar sesion", it)
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
    }

    private fun resetCatalogState() {
        homeCatalog = null
        homeSections = emptyList()
        searchableItems = emptyList()
        channelLineup = emptyList()
        selectedHero = null
        isLoaded = false
        errorMessage = null
        currentItem = null
        currentStreamIndex = 0
        activePlaybackLineup = emptyList()
        currentMode = MainMode.Home
        channelFilters = CatalogFilters()
        movieFilters = CatalogFilters()
        seriesFilters = CatalogFilters()
        channelFilterCountry = null
        movieFilterCountry = null
        seriesFilterCountry = null
    }

    // ── Loading / Error ───────────────────────────────────────────────────────

    @Composable
    private fun LoadingScreen() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.width(520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("WalacTV", color = IptvTextPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text("Cargando contenido...", color = IptvTextMuted, fontSize = 18.sp)
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(IptvSurfaceVariant, RoundedCornerShape(6.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.04f)
                            .height(10.dp)
                            .background(IptvAccent, RoundedCornerShape(6.dp)),
                    )
                }
            }
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
                FocusButton(label = "Reintentar", icon = Icons.Outlined.PlayArrow) { startLoad() }
            }
        }
    }

    // ── Mandatory update ──────────────────────────────────────────────────────

    @Composable
    private fun MandatoryUpdateScreen(updateInfo: AppUpdateInfo) {
        val installed = installedAppVersion
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(700.dp)
                    .background(IptvSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, IptvFocusBorder, RoundedCornerShape(12.dp))
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("Actualizacion obligatoria", color = IptvTextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Debes instalar la nueva version para seguir usando WalacTV.", color = IptvTextMuted, fontSize = 18.sp)
                installed?.let { SettingsRow("Version instalada", "${it.versionName} (${it.versionCode})") }
                SettingsRow("Version requerida", "${updateInfo.latestVersionName} (${updateInfo.latestVersionCode})")
                if (updateInfo.changelog.isNotBlank()) Text(updateInfo.changelog, color = IptvTextPrimary, fontSize = 16.sp)
                Text(updateStatusMessage, color = IptvAccent, fontSize = 15.sp)
                updateErrorMessage?.let { Text(it, color = IptvLive, fontSize = 14.sp) }
                if (isUpdateDownloading) Text("La descarga esta en curso. Al terminar se abrira el instalador.", color = IptvTextMuted, fontSize = 14.sp)
                FocusButton(label = if (isUpdateDownloading) "Descargando..." else "Descargar actualizacion", icon = Icons.Outlined.PlayArrow) {
                    if (!isUpdateDownloading) startUpdateFlow()
                }
                FocusButton(label = if (isCheckingUpdates) "Comprobando..." else "Reintentar", icon = Icons.Outlined.History) {
                    if (!isCheckingUpdates) checkForAppUpdates(showToast = true)
                }
            }
        }
    }

    // ── Main shell ────────────────────────────────────────────────────────────

    @Composable
    private fun MainShell() {
        Row(modifier = Modifier.fillMaxSize()) {
            SideRail()
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (currentMode) {
                    MainMode.Home     -> HomeContent()
                    MainMode.TV       -> GuideContent(ContentKind.CHANNEL)
                    MainMode.Events   -> GuideContent(ContentKind.EVENT)
                    MainMode.Movies   -> VodGridContent(ContentKind.MOVIE)
                    MainMode.Series   -> VodGridContent(ContentKind.SERIES)
                    MainMode.Settings -> SettingsContent()
                }
            }
        }
    }

    // ── Side rail ─────────────────────────────────────────────────────────────

    @Composable
    private fun SideRail() {
        val railItems = buildDefaultSideRailEntries().map(::toNavItem)
        val focusRequesters = remember { List(railItems.size + 1) { FocusRequester() } }

        val railWidth by animateDpAsState(
            targetValue = if (isRailExpanded) 248.dp else 78.dp,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "railWidthAnim",
        )

        Column(
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(IptvSurface)
                .border(1.dp, IptvSurfaceVariant)
                .focusable()
                .onFocusChanged { isRailExpanded = it.hasFocus }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                        keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    ) {
                        val index = railItems.indexOfFirst { it.mode != null && currentMode == it.mode }
                        if (index >= 0) focusRequesters[index].requestFocus()
                        else focusRequesters.last().requestFocus()
                        true
                    } else false
                },
        ) {
            Box(modifier = Modifier.height(100.dp)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isRailExpanded,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(150)),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                        Text("WalacTV", color = IptvTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text("Navegacion", color = IptvTextMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                railItems.forEachIndexed { index, item ->
                    NavigationItem(
                        icon = item.icon,
                        label = item.label,
                        selected = item.mode != null && currentMode == item.mode,
                        expanded = isRailExpanded,
                        modifier = Modifier.focusRequester(focusRequesters[index]),
                    ) { item.onClick?.invoke() ?: item.mode?.let(::changeMode) }
                }
            }

            Box(modifier = Modifier.padding(10.dp)) {
                NavigationItem(
                    icon = Icons.Outlined.Settings,
                    label = "Ajustes",
                    selected = currentMode == MainMode.Settings,
                    expanded = isRailExpanded,
                    modifier = Modifier.focusRequester(focusRequesters.last()),
                ) { changeMode(MainMode.Settings) }
            }
        }
    }

    private fun changeMode(newMode: MainMode) {
        if (currentMode == newMode) return
        currentMode = newMode
        selectedHero = defaultItemForMode(newMode)
        when (newMode) {
            MainMode.TV     -> ensureFiltersLoaded(ContentKind.CHANNEL)
            MainMode.Movies -> ensureFiltersLoaded(ContentKind.MOVIE)
            MainMode.Series -> ensureFiltersLoaded(ContentKind.SERIES)
            else            -> Unit
        }
    }

    private fun ensureFiltersLoaded(kind: ContentKind, country: String? = null) {
        val alreadyLoaded = when (kind) {
            ContentKind.CHANNEL -> channelFilters.countries.isNotEmpty() && channelFilterCountry == country
            ContentKind.MOVIE   -> movieFilters.countries.isNotEmpty() && movieFilterCountry == country
            ContentKind.SERIES  -> seriesFilters.countries.isNotEmpty() && seriesFilterCountry == country
            ContentKind.EVENT   -> true
        }
        if (alreadyLoaded) return

        scope.launch {
            runCatching { repository.loadCatalogFilters(kind, country) }
                .onSuccess { filters ->
                    when (kind) {
                        ContentKind.CHANNEL -> { channelFilters = filters; channelFilterCountry = country }
                        ContentKind.MOVIE   -> { movieFilters = filters; movieFilterCountry = country }
                        ContentKind.SERIES  -> { seriesFilters = filters; seriesFilterCountry = country }
                        ContentKind.EVENT   -> Unit
                    }
                }
                .onFailure { Log.e(TAG, "No se pudieron cargar filtros para $kind", it) }
        }
    }

    private fun openSearch() {
        isRailExpanded = false
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, SearchFragment.newInstance(searchableItems))
            .addToBackStack("SearchFragment")
            .commit()
    }

    private fun toNavItem(entry: SideRailEntry): NavItem {
        return when (entry.destination) {
            SideRailDestination.SEARCH -> NavItem(Icons.Outlined.Search, entry.label, onClick = ::openSearch)
            SideRailDestination.HOME -> NavItem(Icons.Outlined.Home, entry.label, MainMode.Home)
            SideRailDestination.EVENTS -> NavItem(Icons.Outlined.Event, entry.label, MainMode.Events)
            SideRailDestination.TV -> NavItem(Icons.Outlined.LiveTv, entry.label, MainMode.TV)
            SideRailDestination.MOVIES -> NavItem(Icons.Outlined.Movie, entry.label, MainMode.Movies)
            SideRailDestination.SERIES -> NavItem(Icons.Outlined.Tv, entry.label, MainMode.Series)
        }
    }

    private fun defaultItemForMode(mode: MainMode): CatalogItem? = when (mode) {
        MainMode.Home     -> homeSections.firstNotNullOfOrNull { it.items.firstOrNull() }
        MainMode.TV       -> searchableItems.firstOrNull { it.kind == ContentKind.CHANNEL }
        MainMode.Events   -> searchableItems.firstOrNull { it.kind == ContentKind.EVENT }
        MainMode.Movies   -> searchableItems.firstOrNull { it.kind == ContentKind.MOVIE }
        MainMode.Series   -> searchableItems.firstOrNull { it.kind == ContentKind.SERIES }
        MainMode.Settings -> null
    }

    // ── Navigation item ───────────────────────────────────────────────────────

    @Composable
    private fun NavigationItem(
        icon: ImageVector,
        label: String,
        selected: Boolean,
        expanded: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        var isFocused by remember { mutableStateOf(false) }
        val bgColor = when { isFocused -> IptvFocusBg; selected -> IptvCard; else -> Color.Transparent }
        val borderColor = when { isFocused -> IptvFocusBorder; selected -> IptvSurfaceVariant; else -> Color.Transparent }
        val contentColor = if (isFocused || selected) IptvTextPrimary else IptvTextMuted

        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(bgColor, RoundedCornerShape(8.dp))
                .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(8.dp))
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { onClick() }
                .padding(horizontal = if (expanded) 14.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(20.dp))
            AnimatedVisibility(visible = expanded, enter = fadeIn(tween(300)), exit = fadeOut(tween(150))) {
                Row {
                    Spacer(Modifier.width(14.dp))
                    Text(label, color = contentColor, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    // ── Home ──────────────────────────────────────────────────────────────────

    @Composable
    private fun HomeContent() {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item { ScreenHeader(title = "Inicio", subtitle = "Acceso rapido a TV, eventos y ultimos canales") }
            items(homeSections) { section ->
                ContentSection(section = section) { selectedHero = it }
            }
        }
    }

    @Composable
    private fun ContentSection(section: BrowseSection, onFocused: (CatalogItem) -> Unit) {
        val lazyListState = rememberLazyListState()

        LaunchedEffect(section.items) {
            if (section.items.firstOrNull()?.kind == ContentKind.EVENT) {
                val index = section.items.indexOfFirst { isLikelyLiveNow(it) }
                if (index > 0) lazyListState.scrollToItem(index)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(section.title, color = IptvTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Text(sectionKindLabel(section.items), color = IptvTextMuted, fontSize = 14.sp)
            }
            LazyRow(state = lazyListState, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(section.items) { item ->
                    MediaCard(item = item, onFocused = { onFocused(item) }) { handleCardClick(item, section.items) }
                }
            }
        }
    }

    // ── Guide (TV / Events) ───────────────────────────────────────────────────

    @Composable
    private fun GuideContent(kind: ContentKind) {
        val isEventGuide = kind == ContentKind.EVENT
        var selectedCountry by remember { mutableStateOf(ALL_OPTION) }
        var selectedGroup by remember { mutableStateOf(ALL_OPTION) }
        var searchQuery by remember { mutableStateOf("") }
        var showCountryDialog by remember { mutableStateOf(false) }
        var showGroupDialog by remember { mutableStateOf(false) }
        val countryFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        val groupFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        val lazyGridState = rememberLazyGridState()

        var pageItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
        var eventItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
        var currentPage by remember { mutableStateOf(0) }
        var hasNext by remember { mutableStateOf(true) }
        var pageLoading by remember { mutableStateOf(false) }

        val countryOptions = remember(kind, channelFilters) {
            if (isEventGuide) {
                listOf(CatalogFilterOption(ALL_OPTION, "Todos"))
            } else {
                buildList {
                    add(CatalogFilterOption(ALL_OPTION, "Todos"))
                    if (kind == ContentKind.CHANNEL) add(CatalogFilterOption(FAVORITES_GROUP, "Favoritos"))
                    channelFilters.countries.forEach(::add)
                }
            }
        }
        val groupOptions = remember(kind, channelFilters, eventItems) {
            if (isEventGuide) {
                buildList {
                    add(CatalogFilterOption(ALL_OPTION, "Todos"))
                    eventItems
                        .map { it.group.trim() }
                        .filter(String::isNotBlank)
                        .distinct()
                        .sorted()
                        .forEach { add(CatalogFilterOption(it, it)) }
                }
            } else {
                buildList {
                    add(CatalogFilterOption(ALL_OPTION, "Todos"))
                    channelFilters.groups.forEach(::add)
                }
            }
        }

        val displayItems = remember(selectedCountry, selectedGroup, pageItems, eventItems, searchQuery) {
            val base = if (isEventGuide) {
                eventItems
            } else if (selectedCountry == FAVORITES_GROUP) {
                val favIds = channelStateStore.favoriteIds()
                pageItems.filter { favIds.contains(it.stableId) }
            } else {
                pageItems
            }

            val grouped = if (isEventGuide && selectedGroup != ALL_OPTION) {
                base.filter { it.group == selectedGroup }
            } else {
                base
            }

            val filtered = if (searchQuery.isBlank()) {
                grouped
            } else {
                grouped.filter {
                    matchesFilterSearch(it.title, searchQuery) ||
                        matchesFilterSearch(it.group, searchQuery) ||
                        matchesFilterSearch(it.subtitle, searchQuery) ||
                        matchesFilterSearch(it.description, searchQuery)
                }
            }

            if (isEventGuide) {
                filtered.sortedWith(
                    compareBy<CatalogItem> { it.badgeText }
                        .thenBy { it.title },
                )
            } else {
                filtered.sortedBy { it.channelNumber ?: Int.MAX_VALUE }
            }
        }

        // Precarga filtros al entrar
        LaunchedEffect(kind) {
            if (isEventGuide) {
                runCatching { repository.loadEventsOnly() }
                    .onSuccess { catalog ->
                        eventItems = catalog.sections
                            .flatMap(BrowseSection::items)
                            .distinctBy(CatalogItem::stableId)
                    }
                    .onFailure { Log.e(TAG, "No se pudieron cargar los eventos", it) }
            } else {
                ensureFiltersLoaded(kind, null)
            }
        }

        // Auto-scroll al evento en vivo
        LaunchedEffect(displayItems) {
            if (isEventGuide && displayItems.isNotEmpty()) {
                val index = displayItems.indexOfFirst { isLikelyLiveNow(it) }
                if (index > 0) lazyGridState.scrollToItem(index)
            }
        }

        // Recarga grupos cuando cambia el país
        LaunchedEffect(selectedCountry) {
            if (isEventGuide) return@LaunchedEffect
            searchQuery = ""
            selectedGroup = ALL_OPTION
            if (selectedCountry != ALL_OPTION && selectedCountry != FAVORITES_GROUP) {
                val country = selectedCountry
                scope.launch {
                    runCatching { repository.loadCatalogFilters(kind, country) }
                        .onSuccess { filters ->
                            channelFilters = channelFilters.copy(groups = filters.groups)
                            channelFilterCountry = country
                        }
                        .onFailure { Log.e(TAG, "No se pudieron cargar grupos para $country", it) }
                }
            } else if (selectedCountry == ALL_OPTION) {
                scope.launch {
                    runCatching { repository.loadCatalogFilters(kind, null) }
                        .onSuccess { filters -> channelFilters = filters }
                        .onFailure { Log.e(TAG, "No se pudieron restaurar grupos", it) }
                }
            }
        }

        // Carga página 1 cuando cambian los filtros
        LaunchedEffect(selectedCountry, selectedGroup, searchQuery, channelFilters) {
            if (isEventGuide) return@LaunchedEffect
            if (selectedCountry == FAVORITES_GROUP) return@LaunchedEffect

            val country = selectedCountry.takeUnless { it == ALL_OPTION }

            if (channelFilters.countries.isEmpty()) {
                ensureFiltersLoaded(kind, null)
                return@LaunchedEffect
            }

            pageItems = emptyList()
            currentPage = 0
            hasNext = true
            pageLoading = false

            val group = selectedGroup.takeUnless { it == ALL_OPTION }
            val search = searchQuery.takeIf { it.isNotBlank() }

            runCatching { repository.loadCatalogPage(kind, 1, country, group, search) }
                .onSuccess { page -> pageItems = page.items; currentPage = 1; hasNext = page.hasNext }
                .onFailure { Log.e(TAG, "No se pudo cargar pagina 1 de $kind", it) }
        }

        // Carga páginas siguientes al hacer scroll
        LaunchedEffect(lazyGridState, selectedCountry, selectedGroup, searchQuery, hasNext, currentPage) {
            if (isEventGuide) return@LaunchedEffect
            if (selectedCountry == FAVORITES_GROUP || !hasNext || currentPage <= 0) return@LaunchedEffect

            snapshotFlow { lazyGridState.layoutInfo }
                .map { info -> (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount }
                .distinctUntilChanged()
                .filter { (lastVisibleIndex, totalItemsCount) ->
                    lastVisibleIndex >= 0 && totalItemsCount > 0 && lastVisibleIndex >= totalItemsCount - PAGINATION_PREFETCH_DISTANCE
                }
                .collect {
                    if (pageLoading || !hasNext) return@collect

                    pageLoading = true
                    val country = selectedCountry.takeUnless { it == ALL_OPTION }
                    val group = selectedGroup.takeUnless { it == ALL_OPTION }
                    val search = searchQuery.takeIf { it.isNotBlank() }
                    val next = currentPage + 1
                    runCatching { repository.loadCatalogPage(kind, next, country, group, search) }
                        .onSuccess { page ->
                            pageItems = (pageItems + page.items).distinctBy(CatalogItem::stableId)
                            currentPage = page.page.coerceAtLeast(next)
                            hasNext = page.hasNext
                        }
                        .onFailure { Log.e(TAG, "No se pudo cargar pagina $next de $kind", it) }
                    pageLoading = false
                }
        }

        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ScreenHeader(title = screenTitle(kind), subtitle = if (kind == ContentKind.CHANNEL) "Filtra por pais y grupo" else "Filtra por grupo")
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FilterTopBar(
                    showIdioma = kind == ContentKind.CHANNEL,
                    selectedIdioma = countryOptions.firstOrNull { it.value == selectedCountry }?.label ?: selectedCountry,
                    selectedGrupo = groupOptions.firstOrNull { it.value == selectedGroup }?.label ?: selectedGroup,
                    onIdiomaClicked = { showCountryDialog = true },
                    onGrupoClicked = { showGroupDialog = true },
                    idiomaFocusRequester = countryFocusRequester,
                    grupoFocusRequester = groupFocusRequester,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchFocusRequester = searchFocusRequester,
                    idiomaLabel = COUNTRY_FILTER_LABEL,
                )
                if (displayItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (searchQuery.isNotBlank()) "No hay resultados para \"$searchQuery\"" else "No hay contenido disponible",
                            color = IptvTextMuted, fontSize = 18.sp,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        state = lazyGridState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        gridItems(displayItems) { item ->
                            MediaCard(item = item, onFocused = { selectedHero = item }) { handleCardClick(item, displayItems) }
                        }
                    }
                }
            }
        }

        if (showCountryDialog) {
            FilterDialog(
                title = "Selecciona pais",
                options = countryOptions,
                selectedOption = selectedCountry,
                onOptionSelected = { selectedCountry = it.value; showCountryDialog = false },
                onDismiss = { showCountryDialog = false },
            )
        }
        if (showGroupDialog) {
            FilterDialog(
                title = "Selecciona grupo",
                options = groupOptions,
                selectedOption = selectedGroup,
                onOptionSelected = { selectedGroup = it.value; showGroupDialog = false },
                onDismiss = { showGroupDialog = false },
            )
        }
    }

    // ── VOD grid (Movies / Series) ────────────────────────────────────────────
    //
    // FIXES aplicados:
    //  1. activeFilters era una variable local no reactiva → reemplazada por
    //     currentFilters que lee directamente movieFilters / seriesFilders (State).
    //  2. El LaunchedEffect de página 1 ahora lee `filters` dentro del bloque
    //     en lugar de la copia estática, y tiene movieFilters/seriesFilters
    //     como claves para re-ejecutarse cuando lleguen del repositorio.
    //  3. Eliminado el doble filtrado local por idioma/subgrupo: la API ya
    //     filtra server-side; hacerlo también en cliente vaciaba la lista.

    @Composable
    private fun VodGridContent(kind: ContentKind) {
        var selectedCountry by remember { mutableStateOf(ALL_OPTION) }
        var selectedGroup   by remember { mutableStateOf(ALL_OPTION) }
        var searchQuery     by remember { mutableStateOf("") }
        var showCountryDialog by remember { mutableStateOf(false) }
        var showGroupDialog   by remember { mutableStateOf(false) }
        val countryFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        val groupFocusRequester   = remember { androidx.compose.ui.focus.FocusRequester() }
        val searchFocusRequester  = remember { androidx.compose.ui.focus.FocusRequester() }
        val lazyGridState = rememberLazyGridState()

        var pageItems   by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
        var currentPage by remember { mutableStateOf(0) }
        var hasNext     by remember { mutableStateOf(true) }
        var pageLoading by remember { mutableStateOf(false) }

        // FIX 1: leer directamente los estados del fragment (son reactivos)
        val currentFilters = if (kind == ContentKind.MOVIE) movieFilters else seriesFilters

        val countryOptions = remember(currentFilters) {
            buildList {
                add(CatalogFilterOption(ALL_OPTION, "Todos"))
                currentFilters.countries.forEach(::add)
            }
        }
        val groupOptions = remember(currentFilters) {
            buildList {
                add(CatalogFilterOption(ALL_OPTION, "Todos"))
                currentFilters.groups.forEach(::add)
            }
        }

        // FIX 3: sin filtrado local por idioma/subgrupo (la API ya filtra server-side)
        val displayItems = remember(pageItems, searchQuery) {
            var items = pageItems
            if (searchQuery.isNotBlank()) {
                items = items.filter {
                    matchesFilterSearch(it.title, searchQuery) ||
                            matchesFilterSearch(it.subgrupo, searchQuery)
                }
            }
            if (kind == ContentKind.SERIES) buildSeriesGridItems(items) else items.sortedBy { it.title }
        }

        // Precarga filtros al entrar
        LaunchedEffect(kind) {
            ensureFiltersLoaded(kind, null)
        }

        // Recarga grupos cuando cambia el país
        LaunchedEffect(selectedCountry) {
            searchQuery   = ""
            selectedGroup = ALL_OPTION
            if (selectedCountry != ALL_OPTION) {
                val country = selectedCountry
                scope.launch {
                    runCatching { repository.loadCatalogFilters(kind, country) }
                        .onSuccess { filters ->
                            if (kind == ContentKind.MOVIE) {
                                movieFilters       = movieFilters.copy(groups = filters.groups)
                                movieFilterCountry = country
                            } else {
                                seriesFilters       = seriesFilters.copy(groups = filters.groups)
                                seriesFilterCountry = country
                            }
                        }
                        .onFailure { Log.e(TAG, "No se pudieron cargar grupos para $country", it) }
                }
            } else {
                scope.launch {
                    runCatching { repository.loadCatalogFilters(kind, null) }
                        .onSuccess { filters ->
                            if (kind == ContentKind.MOVIE) movieFilters = filters
                            else seriesFilters = filters
                        }
                        .onFailure { Log.e(TAG, "No se pudieron restaurar grupos", it) }
                }
            }
        }

        // FIX 2: movieFilters y seriesFilters como claves para re-ejecutar
        // cuando los filtros lleguen del repositorio. Dentro del bloque se lee
        // el estado actual (filters) en lugar de la copia estática previa.
        LaunchedEffect(selectedCountry, selectedGroup, searchQuery, movieFilters, seriesFilters) {
            val filters = if (kind == ContentKind.MOVIE) movieFilters else seriesFilters

            if (filters.countries.isEmpty()) {
                ensureFiltersLoaded(kind, null)
                return@LaunchedEffect
            }

            pageItems   = emptyList()
            currentPage = 0
            hasNext     = true
            pageLoading = false

            val country = selectedCountry.takeUnless { it == ALL_OPTION }
            val group   = selectedGroup.takeUnless   { it == ALL_OPTION }
            val search  = searchQuery.takeIf         { it.isNotBlank() }

            runCatching { repository.loadCatalogPage(kind, 1, country, group, search) }
                .onSuccess { page -> pageItems = page.items; currentPage = 1; hasNext = page.hasNext }
                .onFailure { Log.e(TAG, "No se pudo cargar pagina 1 de $kind", it) }
        }

        // Carga páginas siguientes al hacer scroll
        LaunchedEffect(lazyGridState, selectedCountry, selectedGroup, searchQuery, hasNext, currentPage) {
            if (!hasNext || currentPage <= 0) return@LaunchedEffect

            snapshotFlow { lazyGridState.layoutInfo }
                .map   { info -> (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount }
                .distinctUntilChanged()
                .filter { (lastVisibleIndex, totalItemsCount) ->
                    lastVisibleIndex >= 0 && totalItemsCount > 0 &&
                            lastVisibleIndex >= totalItemsCount - PAGINATION_PREFETCH_DISTANCE
                }
                .collect {
                    if (pageLoading || !hasNext) return@collect
                    pageLoading = true

                    val country = selectedCountry.takeUnless { it == ALL_OPTION }
                    val group   = selectedGroup.takeUnless   { it == ALL_OPTION }
                    val search  = searchQuery.takeIf         { it.isNotBlank() }
                    val next    = currentPage + 1

                    runCatching { repository.loadCatalogPage(kind, next, country, group, search) }
                        .onSuccess { page ->
                            pageItems   = (pageItems + page.items).distinctBy(CatalogItem::stableId)
                            currentPage = page.page.coerceAtLeast(next)
                            hasNext     = page.hasNext
                        }
                        .onFailure { Log.e(TAG, "No se pudo cargar pagina $next de $kind", it) }
                    pageLoading = false
                }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ScreenHeader(title = screenTitle(kind), subtitle = "Catalogo visual con filtro de grupo")
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FilterTopBar(
                    showIdioma           = true,
                    selectedIdioma       = countryOptions.firstOrNull { it.value == selectedCountry }?.label ?: selectedCountry,
                    selectedGrupo        = groupOptions.firstOrNull { it.value == selectedGroup }?.label ?: selectedGroup,
                    onIdiomaClicked      = { showCountryDialog = true },
                    onGrupoClicked       = { showGroupDialog = true },
                    idiomaFocusRequester = countryFocusRequester,
                    grupoFocusRequester  = groupFocusRequester,
                    searchQuery          = searchQuery,
                    onSearchQueryChange  = { searchQuery = it },
                    searchFocusRequester = searchFocusRequester,
                    idiomaLabel          = "Idioma",
                )
                if (displayItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (searchQuery.isNotBlank()) "No hay resultados para \"$searchQuery\""
                            else "No hay contenido disponible",
                            color = IptvTextMuted, fontSize = 18.sp,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns               = GridCells.Fixed(5),
                        state                 = lazyGridState,
                        modifier              = Modifier.weight(1f),
                        contentPadding        = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement   = Arrangement.spacedBy(14.dp),
                    ) {
                        gridItems(displayItems) { item ->
                            MediaCard(item = item, onFocused = { selectedHero = item }) { handleCardClick(item, displayItems) }
                        }
                    }
                }
            }
        }

        if (showCountryDialog) {
            FilterDialog(
                title            = COUNTRY_FILTER_DIALOG_TITLE,
                options          = countryOptions,
                selectedOption   = selectedCountry,
                onOptionSelected = { selectedCountry = it.value; showCountryDialog = false },
                onDismiss        = { showCountryDialog = false },
            )
        }
        if (showGroupDialog) {
            FilterDialog(
                title            = "Selecciona grupo",
                options          = groupOptions,
                selectedOption   = selectedGroup,
                onOptionSelected = { selectedGroup = it.value; showGroupDialog = false },
                onDismiss        = { showGroupDialog = false },
            )
        }
    }

    // ── Media card ────────────────────────────────────────────────────────────

    @Composable
    private fun MediaCard(item: CatalogItem, onFocused: () -> Unit, onClick: () -> Unit) {
        var isFocused by remember { mutableStateOf(false) }
        val isChannelOrEvent = item.kind == ContentKind.CHANNEL || item.kind == ContentKind.EVENT
        val cardWidth = if (isChannelOrEvent) 190.dp else 140.dp
        val imageHeight = if (isChannelOrEvent) 107.dp else 200.dp

        Column(
            modifier = Modifier
                .width(cardWidth)
                .background(if (isFocused) IptvFocusBg else IptvCard, RoundedCornerShape(10.dp))
                .border(if (isFocused) 2.dp else 1.dp, if (isFocused) IptvFocusBorder else IptvSurfaceVariant, RoundedCornerShape(10.dp))
                .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocused() }
                .clickable { onClick() },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .background(IptvSurfaceVariant, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    item.kind == ContentKind.EVENT -> EventSportPlaceholder(item)
                    item.imageUrl.isNotBlank() -> RemoteImage(
                        url = item.imageUrl,
                        width = 300,
                        height = 400,
                        scaleType = if (item.kind == ContentKind.CHANNEL) ScaleType.FIT_CENTER else ScaleType.CENTER_CROP,
                    )
                    else -> PlaceholderIcon(kind = item.kind)
                }
                item.badgeText
                    .takeIf { it.isNotBlank() && it !in REDUNDANT_BADGES && item.kind != ContentKind.CHANNEL }
                    ?.let { badge ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .background(if (item.kind == ContentKind.EVENT) IptvLive else IptvSurface, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text(badge, color = IptvTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                }
            }
            val isVod = item.kind == ContentKind.MOVIE || item.kind == ContentKind.SERIES
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isChannelOrEvent) Modifier.height(78.dp) else Modifier.height(64.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = if (isVod) Arrangement.Center else Arrangement.Top,
            ) {
                Text(
                    item.normalizedTitle ?: item.title,
                    color = IptvTextPrimary,
                    fontSize = if (isChannelOrEvent) 15.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!isVod) {
                    Spacer(Modifier.weight(1f))
                    val rawSubtitle = item.subtitle
                    val displaySubtitle = if (item.badgeText.isNotBlank() && rawSubtitle.contains(item.badgeText)) {
                        rawSubtitle.replace(item.badgeText, "").replace("•", "").trim()
                    } else {
                        rawSubtitle
                    }.ifBlank { item.group.ifBlank { kindLabel(item.kind) } }
                    Text(
                        displaySubtitle,
                        color = IptvTextMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    // ── Detail panel ──────────────────────────────────────────────────────────

    @Composable
    private fun DetailPanel(item: CatalogItem, modifier: Modifier = Modifier, primaryActionLabel: String, onPrimaryAction: () -> Unit) {
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
                    when {
                        item.kind == ContentKind.EVENT -> EventSportPlaceholder(item, emojiSize = 72.sp)
                        item.imageUrl.isNotBlank() -> RemoteImage(url = item.imageUrl, width = 360, height = 480, scaleType = if (item.kind == ContentKind.CHANNEL) ScaleType.FIT_CENTER else ScaleType.CENTER_CROP)
                        else -> PlaceholderIcon(kind = item.kind, size = 48.dp)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(item.title, color = IptvTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOf(item.group.ifBlank { null }, item.subtitle.ifBlank { null }, kindLabel(item.kind)).filterNotNull().joinToString("  •  "), color = IptvTextMuted, fontSize = 16.sp)
                    item.badgeText
                        .takeIf { it.isNotBlank() && it !in REDUNDANT_BADGES }
                        ?.let { badge ->
                        Box(modifier = Modifier.background(if (item.kind == ContentKind.EVENT) IptvLive else IptvCard, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(badge, color = IptvTextPrimary, fontSize = 12.sp)
                        }
                    }
                    Text(item.description.ifBlank { "Listo para reproducir" }, color = IptvTextPrimary, fontSize = 16.sp, maxLines = 7, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    FocusButton(label = primaryActionLabel, icon = Icons.Outlined.PlayArrow, onClick = onPrimaryAction)
                }
            }
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    @Composable
    private fun SettingsContent() {
        var preferredLanguage by remember { mutableStateOf(PreferencesManager.getPreferredLanguageOrDefault()) }
        var showLanguageDialog by remember { mutableStateOf(false) }
        var showChangelogDialog by remember { mutableStateOf(false) }
        val languageFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        val availableLanguages = listOf("ES" to "Español", "EN" to "Inglés")
        val installedVersionLabel = installedAppVersion?.let { "${it.versionName} (${it.versionCode})" } ?: "Desconocida"
        val hasUpdate = availableUpdate?.let { evaluateAppUpdate(installedAppVersion ?: InstalledAppVersion("0", 0), it) != AppUpdateAvailability.UP_TO_DATE } == true
        val updateActionLabel = when {
            isUpdateDownloading -> "Descargando..."
            isCheckingUpdates   -> "Comprobando..."
            hasUpdate           -> "Descargar actualización"
            else                -> "Buscar actualizaciones"
        }

        Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            ScreenHeader(title = "Ajustes", subtitle = "Actualizaciones, idioma y sesion")
            Column(
                modifier = Modifier
                    .width(760.dp)
                    .background(IptvSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(10.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val selectedLanguageLabel = availableLanguages.find { it.first == preferredLanguage }?.second ?: "Español"
                SettingsRowClickable(label = "Idioma de series", value = selectedLanguageLabel) { showLanguageDialog = true }
                SettingsRow("Version de la app", installedVersionLabel)
                SettingsRow("Canales cargados", channelLineup.size.toString())
                SettingsRow("Contenido indexado", searchableItems.size.toString())
                updateErrorMessage?.let { Text(it, color = IptvLive, fontSize = 14.sp) }

                val update = availableUpdate
                if (hasUpdate && update != null) {
                    Text(
                        "Ultima version: v${update.latestVersionName}",
                        color = IptvAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                } else if (update != null) {
                    Text(
                        "Actualizado a la ultima version",
                        color = IptvOnline,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                val changelogText = update?.changelog
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!changelogText.isNullOrBlank()) {
                        FocusButton(
                            label = "Ver novedades",
                            icon = Icons.Outlined.Info,
                            modifier = Modifier.weight(1f),
                        ) { showChangelogDialog = true }
                    }
                    FocusButton(
                        label = updateActionLabel,
                        icon = Icons.Outlined.PlayArrow,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (!isUpdateDownloading && !isCheckingUpdates) {
                            scope.launch {
                                isCheckingUpdates = true

                                val remoteUpdate = runCatching {
                                    appUpdateRepository.fetchRemoteUpdate()
                                }.getOrNull()

                                if (remoteUpdate != null) {
                                    appUpdateRepository.cacheUpdate(remoteUpdate)
                                    availableUpdate = remoteUpdate
                                }

                                val installed = installedAppVersion
                                val latest = remoteUpdate ?: availableUpdate

                                isCheckingUpdates = false

                                if (latest == null || installed == null ||
                                    installed.versionName.trim() == latest.latestVersionName.trim()
                                ) {
                                    Toast.makeText(
                                        requireContext(),
                                        "Ya tienes la última versión instalada",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    startUpdateFlow()
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FocusButton(label = "Cerrar sesion", icon = Icons.Outlined.Settings) { performSignOut() }
                }
            }
        }

        if (showLanguageDialog) {
            FilterDialog(
                title = "Idioma preferido",
                options = availableLanguages.map { CatalogFilterOption(value = it.first, label = it.second) },
                selectedOption = preferredLanguage,
                onOptionSelected = {
                    PreferencesManager.preferredLanguage = it.value
                    preferredLanguage = it.value
                    showLanguageDialog = false
                },
                onDismiss = { showLanguageDialog = false; runCatching { languageFocusRequester.requestFocus() } },
            )
        }

        if (showChangelogDialog) {
            val update = availableUpdate ?: mandatoryUpdate
            if (update != null) {
                ChangelogDialog(
                    versionName = update.latestVersionName,
                    markdown = update.changelog.ifBlank { "Sin notas de la version." },
                    onDismiss = { showChangelogDialog = false },
                )
            } else {
                val installed = installedAppVersion
                ChangelogDialog(
                    versionName = installed?.versionName ?: "Desconocida",
                    markdown = "No hay informacion de actualizacion disponible en este momento.",
                    onDismiss = { showChangelogDialog = false },
                )
            }
        }
    }

    @Composable
    private fun SettingsRowClickable(label: String, value: String, onClick: () -> Unit) {
        var isFocused by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isFocused) IptvFocusBg else Color.Transparent, RoundedCornerShape(8.dp))
                .border(1.dp, if (isFocused) IptvFocusBorder else Color.Transparent, RoundedCornerShape(8.dp))
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { onClick() }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = IptvTextPrimary, fontSize = 16.sp)
            Text("$value ▸", color = IptvAccent, fontSize = 16.sp)
        }
    }

    @Composable
    private fun SettingsRow(label: String, value: String) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = IptvTextPrimary, fontSize = 16.sp)
            Text(value, color = IptvTextMuted, fontSize = 16.sp)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Composable
    private fun FocusButton(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
        var isFocused by remember { mutableStateOf(false) }
        Row(
            modifier = modifier
                .height(52.dp)
                .background(if (isFocused) IptvAccent else IptvCard, RoundedCornerShape(8.dp))
                .border(if (isFocused) 2.dp else 1.dp, if (isFocused) IptvTextPrimary else IptvSurfaceVariant, RoundedCornerShape(8.dp))
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { onClick() }
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
            ContentKind.EVENT   -> Icons.Outlined.Event
            ContentKind.CHANNEL -> Icons.Outlined.LiveTv
            ContentKind.MOVIE   -> Icons.Outlined.Movie
            ContentKind.SERIES  -> Icons.Outlined.Tv
        }
        Icon(icon, contentDescription = null, tint = IptvTextMuted, modifier = Modifier.size(size))
    }

    @Composable
    private fun EventSportPlaceholder(item: CatalogItem, emojiSize: TextUnit = 48.sp) {
        val category = item.group.lowercase()
        val text = java.text.Normalizer.normalize(category, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        val emoji = when {
            text.contains("futbol") -> "⚽"; text.contains("baloncesto") -> "🏀"
            text.contains("tenis") -> "🎾"; text.contains("motociclismo") || text.contains("automovilismo") -> "🏎️"
            text.contains("mma") || text.contains("boxeo") -> "🥊"; text.contains("rugby") -> "🏈"
            text.contains("balonmano") -> "🤾"; text.contains("hockey") -> "🏒"
            text.contains("padel") -> "🏸"; else -> "🏆"
        }
        val colors = when {
            text.contains("futbol") -> listOf(Color(0xFF0B6E4F), Color(0xFF1A936F))
            text.contains("baloncesto") -> listOf(Color(0xFF7F4F24), Color(0xFFD68C45))
            text.contains("tenis") -> listOf(Color(0xFF254441), Color(0xFF43AA8B))
            text.contains("motociclismo") || text.contains("automovilismo") -> listOf(Color(0xFF1D3557), Color(0xFF457B9D))
            text.contains("mma") || text.contains("boxeo") -> listOf(Color(0xFF5F0F40), Color(0xFF9A031E))
            text.contains("rugby") -> listOf(Color(0xFF4A1942), Color(0xFF893642))
            text.contains("balonmano") -> listOf(Color(0xFF1E3A5F), Color(0xFF3D5A80))
            text.contains("padel") -> listOf(Color(0xFF2E7D32), Color(0xFF66BB6A))
            text.contains("hockey") -> listOf(Color(0xFF37474F), Color(0xFF78909C))
            else -> listOf(Color(0xFF102A43), Color(0xFFD64550))
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
            Text(text = emoji, fontSize = emojiSize, textAlign = TextAlign.Center)
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
    private fun RemoteImage(url: String, width: Int, height: Int, scaleType: ScaleType) {
        AndroidView(
            factory = { context -> ImageView(context).apply { this.scaleType = scaleType; setBackgroundColor(AndroidColor.TRANSPARENT) } },
            update = { iv -> iv.scaleType = scaleType; Glide.with(iv.context.applicationContext).load(url).override(width, height).dontTransform().into(iv) },
            onRelease = { iv -> runCatching { Glide.with(iv.context.applicationContext).clear(iv) }.onFailure { Log.w(TAG, "No se pudo limpiar imagen", it) } },
            modifier = Modifier.fillMaxSize(),
        )
    }

    // ── Card / section helpers ────────────────────────────────────────────────

    private fun handleCardClick(item: CatalogItem, lineup: List<CatalogItem> = emptyList()) {
        if (item.stableId.startsWith("series_group:")) {
            val fragment = SeriesDetailFragment.newInstance(item.stableId.removePrefix("series_group:"))
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, fragment)
                .addToBackStack("SeriesDetailFragment")
                .commit()
            return
        }
        activePlaybackLineup = lineup.filter { it.kind == item.kind }
        playCatalogItem(item, 0)
    }

    private fun refreshCatalog() {
        homeCatalog = null
        homeSections = emptyList()
        searchableItems = emptyList()
        channelLineup = emptyList()
        activePlaybackLineup = emptyList()
        selectedHero = null
        isLoaded = false
        repository.clearHomeMemoryCache()
        startLoad(forceRefresh = true)
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
        val ids = channelStateStore.favoriteIds()
        val items = channels.filter { ids.contains(it.stableId) }
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

    private fun screenTitle(kind: ContentKind) = when (kind) {
        ContentKind.EVENT   -> "Eventos"
        ContentKind.CHANNEL -> "TV en directo"
        ContentKind.MOVIE   -> "Peliculas"
        ContentKind.SERIES  -> "Series"
    }

    private fun kindLabel(kind: ContentKind) = when (kind) {
        ContentKind.EVENT   -> "Evento"
        ContentKind.CHANNEL -> "Canal"
        ContentKind.MOVIE   -> "Pelicula"
        ContentKind.SERIES  -> "Serie"
    }

    private fun sectionKindLabel(items: List<CatalogItem>): String {
        val kind = items.firstOrNull()?.kind ?: return ""
        val count = items.size
        val (singular, plural) = when (kind) {
            ContentKind.CHANNEL -> "canal" to "canales"
            ContentKind.EVENT   -> "evento" to "eventos"
            ContentKind.MOVIE   -> "pelicula" to "peliculas"
            ContentKind.SERIES  -> "serie" to "series"
        }
        return if (count == 1) "1 $singular" else "$count $plural"
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

    // ── Playback ──────────────────────────────────────────────────────────────

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun playCatalogItem(item: CatalogItem, optionIndex: Int) {
        if (item.kind == ContentKind.EVENT) {
            scope.launch {
                val resolved = repository.resolveEventItem(item)
                if (resolved.streamOptions.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.no_streams_available, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                playResolvedCatalogItem(resolved, optionIndex.coerceIn(resolved.streamOptions.indices))
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

        if (item.kind == ContentKind.CHANNEL) channelStateStore.markRecent(item)

        val fm = requireActivity().supportFragmentManager
        fm.findFragmentById(R.id.player_container)?.let { fm.beginTransaction().remove(it).commitNow() }

        val playerFragment = PlayerFragment(
            streamUrl = stream.url,
            overlayNumber = when {
                item.kind == ContentKind.CHANNEL && item.channelNumber != null -> "CH ${item.channelNumber}"
                item.kind == ContentKind.EVENT -> "EN DIRECTO"
                else -> item.kind.name
            },
            overlayTitle = item.title,
            overlayMeta = listOf(item.subtitle, stream.label).filter { it.isNotBlank() }.joinToString("  •  ").ifBlank { item.description },
            contentKind = item.kind,
            onNavigateChannel = ::navigateChannel,
            onNavigateOption = ::navigateOption,
            onDirectChannelNumber = ::navigateToChannelNumber,
            onToggleFavorite = { toggleFavorite(item) },
            onOpenFavorites = ::openFavoriteChannel,
            onOpenRecents = ::openRecentChannel,
            onOpenGuide = ::openGuideOverlay,
            streamOptionLabels = item.streamOptions.map { it.label },
            currentOptionIndex = optionIndex,
        )
        fm.beginTransaction().replace(R.id.player_container, playerFragment, "player_fragment").commitNow()
        val container = requireActivity().findViewById<FrameLayout>(R.id.player_container)
        container.visibility = View.VISIBLE
        container.isFocusable = true
        container.isFocusableInTouchMode = true
        container.requestFocus()
    }

    private fun navigateChannel(direction: Int) {
        val current = currentItem ?: return
        val lineup = activePlaybackLineup.ifEmpty { channelLineup }
        if (lineup.isEmpty()) return
        val idx = lineup.indexOfFirst { it.stableId == current.stableId }.takeIf { it != -1 } ?: return
        val target = idx + direction
        if (target !in lineup.indices) return
        playCatalogItem(lineup[target], 0)
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
        val ids = channelStateStore.favoriteIds()
        val match = channelLineup.firstOrNull { ids.contains(it.stableId) } ?: return false
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

    private fun openGuideOverlay(initialGroup: String?) {
        val fm = requireActivity().supportFragmentManager
        fm.setFragmentResultListener(GuideFragment.REQUEST_KEY, this) { _, bundle ->
            val channelId = bundle.getString(GuideFragment.KEY_CHANNEL_ID) ?: return@setFragmentResultListener
            val item = channelLineup.firstOrNull { it.stableId == channelId } ?: return@setFragmentResultListener
            playCatalogItem(item, 0)
        }

        val guideFragment = GuideFragment().apply {
            if (initialGroup != null) {
                arguments = Bundle().apply { putString("initial_group", initialGroup) }
            }
        }
        
        fm.beginTransaction()
            .add(R.id.player_container, guideFragment, "guide_fragment")
            .addToBackStack(null)
            .commit()
    }

    private fun isLikelyLiveNow(item: CatalogItem): Boolean {
        if (item.kind != ContentKind.EVENT) return false
        val parsed = runCatching { EVENT_TIME_FORMAT.parse(item.badgeText) }.getOrNull() ?: return false
        val now = Calendar.getInstance()
        val eventCal = Calendar.getInstance().apply {
            time = parsed
            set(Calendar.YEAR, now.get(Calendar.YEAR))
            set(Calendar.MONTH, now.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
        }
        val delta = (now.timeInMillis - eventCal.timeInMillis) / 60_000L
        return delta in -20..180
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private enum class MainMode { Home, TV, Movies, Series, Events, Settings }

    private data class NavItem(
        val icon: ImageVector,
        val label: String,
        val mode: MainMode? = null,
        val activatesOnFocus: Boolean = true,
        val onClick: (() -> Unit)? = null,
    )

    companion object {
        private const val TAG = "ComposeMainFragment"
        private const val ALL_OPTION = "Todos"
        private const val FAVORITES_GROUP = "Favoritos"
        private const val PAGINATION_PREFETCH_DISTANCE = 5
        private val EVENT_TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val REDUNDANT_BADGES = setOf("CINE", "SERIE", "Pelicula", "Serie")
    }
}
