@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.example.walactv

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.example.walactv.local.ContentCacheManager
import com.example.walactv.ui.ComposeRoot
import com.example.walactv.ui.canRequestPackageInstalls
import com.example.walactv.ui.changeMode
import com.example.walactv.ui.checkForAppUpdates
import com.example.walactv.ui.defaultItemForMode
import com.example.walactv.ui.ensureFiltersLoaded
import com.example.walactv.ui.handleCompletedUpdateDownload
import com.example.walactv.ui.restoreCachedUpdateState
import com.example.walactv.ui.startLoad
import com.example.walactv.ui.startUpdateDownload
import com.example.walactv.ui.theme.WalacTVTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.text.SimpleDateFormat
import java.util.Locale

class ComposeMainFragment : Fragment() {

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    internal lateinit var repository: IptvRepository
    internal lateinit var appUpdateRepository: AppUpdateRepository
    internal lateinit var channelStateStore: ChannelStateStore
    internal lateinit var watchProgressRepo: WatchProgressRepository
    internal lateinit var contentCacheManager: ContentCacheManager

    var composeDialogOpen by mutableStateOf(false)
        internal set

    internal var homeCatalog by mutableStateOf<HomeCatalog?>(null)
    internal var homeSections by mutableStateOf<List<BrowseSection>>(emptyList())
    internal var continueWatchingSection by mutableStateOf<BrowseSection?>(null)
    internal var continueWatchingEntries by mutableStateOf<Map<String, WatchProgressItem>>(emptyMap())
    internal var deleteContinueWatchingItem by mutableStateOf<CatalogItem?>(null)
    internal var searchableItems by mutableStateOf<List<CatalogItem>>(emptyList())
    internal var channelLineup by mutableStateOf<List<CatalogItem>>(emptyList())
    internal var channelFilters by mutableStateOf(CatalogFilters())
    internal var movieFilters by mutableStateOf(CatalogFilters())
    internal var seriesFilters by mutableStateOf(CatalogFilters())
    internal var channelFilterCountry by mutableStateOf<String?>(null)
    internal var movieFilterCountry by mutableStateOf<String?>(null)
    internal var seriesFilterCountry by mutableStateOf<String?>(null)
    internal var selectedHero by mutableStateOf<CatalogItem?>(null)
    internal var pendingFocusItem by mutableStateOf<CatalogItem?>(null)
    internal var pendingFocusTrigger by mutableStateOf(0)
    internal var currentMode by mutableStateOf(MainMode.Home)
    internal var isRailExpanded by mutableStateOf(false)
    internal var isSignedIn by mutableStateOf(false)
    internal var loginUsername by mutableStateOf("")
    internal var loginPassword by mutableStateOf("")
    internal var loginError by mutableStateOf<String?>(null)
    internal var isSigningIn by mutableStateOf(false)

    internal var isLoaded by mutableStateOf(false)
    internal var errorMessage by mutableStateOf<String?>(null)
    internal var installedAppVersion by mutableStateOf<InstalledAppVersion?>(null)
    internal var availableUpdate by mutableStateOf<AppUpdateInfo?>(null)
    internal var mandatoryUpdate by mutableStateOf<AppUpdateInfo?>(null)
    internal var updateStatusMessage by mutableStateOf("No comprobado")
    internal var updateErrorMessage by mutableStateOf<String?>(null)
    internal var isCheckingUpdates by mutableStateOf(false)
    internal var isUpdateDownloading by mutableStateOf(false)
    internal var pendingInstallPermission by mutableStateOf(false)

    enum class ContentSyncState { IDLE, CHECKING, SYNCING, READY, ERROR }
    internal var contentSyncState by mutableStateOf(ContentSyncState.IDLE)
    internal var contentSyncError by mutableStateOf<String?>(null)
    internal var currentSyncLabel by mutableStateOf("")
    internal var currentSyncCount by mutableStateOf(0)
    internal var overallSyncProgress by mutableStateOf(0f)

    internal var currentItem: CatalogItem? = null
    internal var currentStreamIndex: Int = 0
    internal var activePlaybackLineup: List<CatalogItem> = emptyList()
    internal var playbackReturnState: PlaybackReturnState? = null
    internal var pendingUpdateDownloadId: Long? = null
    internal var guideInitialGroup: String? = null
    internal var continueWatchingRequestVersion: Int = 0

    internal var showChannelPicker by mutableStateOf(false)
    internal var channelPickerCountry by mutableStateOf(ALL_OPTION)
    internal var channelPickerGroup by mutableStateOf(ALL_OPTION)
    internal var channelPickerQuery by mutableStateOf("")
    internal var channelPickerShowFavorites by mutableStateOf(false)

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
        watchProgressRepo = WatchProgressRepository(requireContext())
        contentCacheManager = ContentCacheManager(requireContext())
        installedAppVersion = appUpdateRepository.installedVersion()
        isSignedIn = repository.hasStoredCredentials()
        loginUsername = repository.currentUsername()

        return ComposeView(requireContext()).apply {
            setContent {
                WalacTVTheme {
                    ComposeRoot(fragment = this@ComposeMainFragment)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        restoreCachedUpdateState()
        checkForAppUpdates()
        if (isSignedIn) startLoad()
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

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── Public navigation API (used by MainActivity) ───────────────────────

    fun currentNavigationMode(): String = currentMode.name

    fun navigateToHome() {
        Log.d(TAG, "navigateToHome called, currentMode=$currentMode")
        if (currentMode == MainMode.Home) return
        changeMode(MainMode.Home)
    }

    fun navigateHomeOnBack(): Boolean {
        Log.d(TAG, "navigateHomeOnBack called, currentMode=$currentMode")
        if (currentMode == MainMode.Home) return false
        changeMode(MainMode.Home)
        return true
    }

    fun restorePlaybackReturnState() {
        val state = playbackReturnState ?: return
        playbackReturnState = null
        Log.d(TAG, "Restoring playback return state: mode=${state.mode}, selectedItemStableId=${state.selectedItemStableId}")
        currentMode = state.mode
        when (state.mode) {
            MainMode.TV     -> ensureFiltersLoaded(ContentKind.CHANNEL)
            MainMode.Movies -> ensureFiltersLoaded(ContentKind.MOVIE)
            MainMode.Series -> ensureFiltersLoaded(ContentKind.SERIES)
            else            -> Unit
        }
        // Buscar en searchableItems Y en homeSections (para CW items)
        selectedHero = searchableItems.firstOrNull { it.stableId == state.selectedItemStableId }
            ?: homeSections.asSequence().flatMap { it.items.asSequence() }.firstOrNull { it.stableId == state.selectedItemStableId }
            ?: defaultItemForMode(currentMode)
        Log.d(TAG, "SelectedHero restored: ${selectedHero?.stableId} title=${selectedHero?.title}")
        pendingFocusItem = selectedHero
        pendingFocusTrigger++
    }

    fun restoreFocusAfterPlayer() {
        Log.d(TAG, "restoreFocusAfterPlayer called - pendingFocusTrigger=$pendingFocusTrigger pendingFocusItem=${pendingFocusItem?.stableId}")
    }

    // ── Inner types ────────────────────────────────────────────────────────

    internal enum class MainMode { Home, TV, Movies, Series, Events, Anime, Settings }

    internal data class NavItem(
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val label: String,
        val mode: MainMode? = null,
        val activatesOnFocus: Boolean = true,
        val onClick: (() -> Unit)? = null,
    )

    internal data class PlaybackReturnState(
        val mode: MainMode,
        val selectedItemStableId: String,
    )

    internal enum class AnimeTab { ON_AIR, LATEST, SEARCH }

    companion object {
        internal const val TAG = "ComposeMainFragment"
        internal const val ALL_OPTION = "Todos"
        internal const val FAVORITES_FILTER = "Favoritos"
        internal val EVENT_TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
        internal val REDUNDANT_BADGES = setOf("CINE", "SERIE", "Pelicula", "Serie")
        private const val COUNTRY_FILTER_LABEL = "País"
        private const val COUNTRY_FILTER_DIALOG_TITLE = "Selecciona país"
    }
}
