@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.example.walactv

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.example.walactv.local.ContentCacheManager
import com.example.walactv.local.PagedContentLoader
import com.example.walactv.ui.compose.ComposeRoot
import com.example.walactv.ui.compose.canRequestPackageInstalls
import com.example.walactv.ui.compose.changeMode
import com.example.walactv.ui.compose.checkForAppUpdates
import com.example.walactv.ui.compose.defaultItemForMode
import com.example.walactv.ui.compose.ensureFiltersLoaded
import com.example.walactv.ui.compose.handleCompletedUpdateDownload
import com.example.walactv.ui.compose.restoreCachedUpdateState
import com.example.walactv.ui.compose.startUpdateFlowIfReady
import com.example.walactv.ui.compose.upsertContinueWatchingEntry
import com.example.walactv.ui.theme.WalacTVTheme
import com.example.walactv.viewmodel.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class ComposeMainFragment : Fragment() {

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    internal val appComponent get() = (requireActivity().application as WalacApp).appComponent
    internal val repository: IptvRepository get() = appComponent.iptvRepository
    internal lateinit var appUpdateRepository: AppUpdateRepository
    internal val channelStateStore: ChannelStateStore get() = appComponent.channelStateStore
    internal val watchProgressRepo: WatchProgressRepository get() = appComponent.watchProgressRepository
    internal val contentCacheManager: ContentCacheManager get() = appComponent.contentCacheManager
    internal val viewModel: HomeViewModel get() = appComponent.homeViewModel

    var composeDialogOpen by mutableStateOf(false)
        internal set

    internal var homeCatalog by mutableStateOf<HomeCatalog?>(null)
    internal var homeSections by mutableStateOf<List<BrowseSection>>(emptyList())
    internal var continueWatchingSection by mutableStateOf<BrowseSection?>(null)
    internal var continueWatchingEntries by mutableStateOf<Map<String, WatchProgressItem>>(emptyMap())
    internal var continueWatchingMenuItem by mutableStateOf<CatalogItem?>(null)
    internal var searchableItems by mutableStateOf<List<CatalogItem>>(emptyList())
    internal var channelLineup by mutableStateOf<List<CatalogItem>>(emptyList())
    internal var channelFilters by mutableStateOf(CatalogFilters())
    internal var movieFilters by mutableStateOf(CatalogFilters())
    internal var seriesFilters by mutableStateOf(CatalogFilters())
    internal var channelFilterCountry by mutableStateOf<String?>(null)
    internal var movieFilterCountry by mutableStateOf<String?>(null)
    internal var seriesFilterCountry by mutableStateOf<String?>(null)
    internal val discoverLoaders: Map<ContentKind, PagedContentLoader> by lazy {
        mapOf(
            ContentKind.MOVIE to PagedContentLoader(contentCacheManager, repository, ContentKind.MOVIE),
            ContentKind.SERIES to PagedContentLoader(contentCacheManager, repository, ContentKind.SERIES),
        )
    }
    internal var discoverFocusedItemStableId by mutableStateOf<String?>(null)
    internal var discoverFocusLocked by mutableStateOf(false)

    internal var selectedHero by mutableStateOf<CatalogItem?>(null)
    internal var pendingFocusItem by mutableStateOf<CatalogItem?>(null)
    internal var pendingFocusTrigger by mutableIntStateOf(0)
    internal var lastHomeFocusTarget by mutableStateOf<HomeFocusTarget?>(null)
    internal var pendingHomeFocusTarget by mutableStateOf<HomeFocusTarget?>(null)
    internal var homeFocusRestoreTrigger by mutableIntStateOf(0)
    internal var contentFocusTrigger by mutableIntStateOf(0)
    internal var searchBackTrigger by mutableIntStateOf(0)
    internal var contentFocusCanOpenRail by mutableStateOf(false)
    internal var suppressEventAutoScroll by mutableStateOf(false)
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
    internal var hasCheckedForUpdates by mutableStateOf(false)
    internal var pendingInstallPermission by mutableStateOf(false)

    enum class ContentSyncState { IDLE, CHECKING, SYNCING, READY, ERROR }
    internal var contentSyncState by mutableStateOf(ContentSyncState.IDLE)
    internal var contentSyncError by mutableStateOf<String?>(null)
    internal var currentSyncLabel by mutableStateOf("")
    internal var currentSyncCount by mutableIntStateOf(0)
    internal var overallSyncProgress by mutableFloatStateOf(0f)

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
        CredentialStore.init(requireContext())
        appUpdateRepository = AppUpdateRepository(requireContext())
        installedAppVersion = appUpdateRepository.installedVersion()
        isSignedIn = repository.hasStoredCredentials()
        loginUsername = repository.currentUsername()

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            requireContext(),
            updateDownloadReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        observeViewModel()

        progressSavedCallback = { upsertContinueWatchingEntry(it) }

        return ComposeView(requireContext()).apply {
            setContent {
                WalacTVTheme {
                    ComposeRoot(fragment = this@ComposeMainFragment)
                }
            }
        }
    }

    override fun onDestroyView() {
        runCatching { requireContext().unregisterReceiver(updateDownloadReceiver) }
        progressSavedCallback = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        restoreCachedUpdateState()
        checkForAppUpdates()
        if (isSignedIn) viewModel.startLoad()
    }

    private fun observeViewModel() {
        scope.launch {
            launch {
                viewModel.homeCatalog.collectLatest { homeCatalog = it }
            }
            launch {
                viewModel.homeSections.collectLatest { homeSections = it }
            }
            launch {
                viewModel.continueWatchingSection.collectLatest { continueWatchingSection = it }
            }
            launch {
                viewModel.continueWatchingEntries.collectLatest { continueWatchingEntries = it }
            }
            launch {
                viewModel.searchableItems.collectLatest { searchableItems = it }
            }
            launch {
                viewModel.channelLineup.collectLatest { channelLineup = it }
            }
            launch {
                viewModel.selectedHero.collectLatest { selectedHero = it }
            }
            launch {
                viewModel.isLoaded.collectLatest { isLoaded = it }
            }
            launch {
                viewModel.errorMessage.collectLatest { errorMessage = it }
            }
            launch {
                viewModel.channelFilters.collectLatest { channelFilters = it }
            }
            launch {
                viewModel.movieFilters.collectLatest { movieFilters = it }
            }
            launch {
                viewModel.seriesFilters.collectLatest { seriesFilters = it }
            }
            launch {
                viewModel.channelFilterCountry.collectLatest { channelFilterCountry = it }
            }
            launch {
                viewModel.movieFilterCountry.collectLatest { movieFilterCountry = it }
            }
            launch {
                viewModel.seriesFilterCountry.collectLatest { seriesFilterCountry = it }
            }
            launch {
                viewModel.contentSyncState.collectLatest { syncState ->
                    contentSyncState = ContentSyncState.valueOf(syncState.name)
                }
            }
            launch {
                viewModel.contentSyncError.collectLatest { contentSyncError = it }
            }
            launch {
                viewModel.currentSyncLabel.collectLatest { currentSyncLabel = it }
            }
            launch {
                viewModel.currentSyncCount.collectLatest { currentSyncCount = it }
            }
            launch {
                viewModel.overallSyncProgress.collectLatest { overallSyncProgress = it }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingInstallPermission && canRequestPackageInstalls()) {
            pendingInstallPermission = false
            startUpdateFlowIfReady()
        }
        if (isSignedIn && isLoaded) viewModel.refreshEvents()
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

    fun restorePlaybackReturnState() {
        val state = playbackReturnState ?: return
        playbackReturnState = null
        Log.d(TAG, "Restoring playback return state: mode=${state.mode}, selectedItemStableId=${state.selectedItemStableId}")
        Log.d("DiscoverFocus", "restorePlaybackReturnState: mode=${state.mode} selectedItemStableId=${state.selectedItemStableId} discoverFocusedId=$discoverFocusedItemStableId contentFocusTrigger=$contentFocusTrigger")
        currentMode = state.mode
        when (state.mode) {
            MainMode.TV       -> ensureFiltersLoaded(ContentKind.CHANNEL)
            MainMode.Discover -> {
                ensureFiltersLoaded(ContentKind.MOVIE)
                ensureFiltersLoaded(ContentKind.SERIES)
            }
            else              -> Unit
        }
        val homeItem = homeSections.asSequence()
            .flatMap { it.items.asSequence() }
            .firstOrNull { it.stableId == state.selectedItemStableId }
        val searchableItem = searchableItems.firstOrNull { it.stableId == state.selectedItemStableId }
        Log.d(
            TAG,
            "TMDB_RESTORE candidates id=${state.selectedItemStableId} " +
                "home=${homeItem.tmdbDebug()} snapshot=${state.selectedItemSnapshot.tmdbDebug()} searchable=${searchableItem.tmdbDebug()}",
        )
        selectedHero = homeItem
            .richestTmdbItem(state.selectedItemSnapshot, searchableItem)
            ?: defaultItemForMode(currentMode)
        Log.d(TAG, "TMDB_RESTORE selected=${selectedHero.tmdbDebug()}")
        pendingFocusItem = selectedHero
        pendingFocusTrigger++
        if (state.mode != MainMode.Home) {
            contentFocusTrigger++
        }
        suppressEventAutoScroll = true
    }

    fun restoreFocusAfterPlayer() {
        Log.d(TAG, "restoreFocusAfterPlayer called - pendingFocusTrigger=$pendingFocusTrigger pendingFocusItem=${pendingFocusItem?.stableId}")
        if (pendingFocusItem != null) {
            pendingFocusTrigger++
        }
    }

    internal fun onSearchBackPressed() {
        searchBackTrigger++
        Log.d("FocusTrace", "onSearchBackPressed: searchBackTrigger=$searchBackTrigger")
    }

    internal fun rememberHomeFocus(
        sectionIndex: Int,
        sectionTitle: String,
        item: CatalogItem,
        itemIndex: Int,
    ) {
        Log.d(TAG, "rememberHomeFocus: [$sectionIndex]'$sectionTitle' item[$itemIndex]='${item.title}' stableId=${item.stableId}")
        lastHomeFocusTarget = HomeFocusTarget(
            sectionIndex = sectionIndex,
            sectionTitle = sectionTitle,
            itemStableId = item.stableId,
            itemIndex = itemIndex,
        )
    }

    internal fun requestHomeFocusRestoreFromRail(): Boolean {
        val target = lastHomeFocusTarget
        pendingHomeFocusTarget = target
        homeFocusRestoreTrigger++
        return target != null
    }

    // ── Inner types ────────────────────────────────────────────────────────

    internal enum class MainMode { Home, TV, Discover, Events, Settings }

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
        val selectedItemSnapshot: CatalogItem?,
    )

    internal data class HomeFocusTarget(
        val sectionIndex: Int,
        val sectionTitle: String,
        val itemStableId: String,
        val itemIndex: Int,
    )

    

    companion object {
        internal const val TAG = "ComposeMainFragment"
        internal const val ALL_OPTION = "Todos"
        internal val EVENT_TIME_FORMAT get() = SimpleDateFormat("HH:mm", Locale.getDefault())
        /** Callback que detail fragments usan para actualizar Continue Watching localmente */
        internal var progressSavedCallback: ((WatchProgressItem) -> Unit)? = null
    }
}

internal fun CatalogItem?.tmdbDebug(): String {
    if (this == null) return "null"
    return "stableId=$stableId kind=$kind title=$title series=$seriesName hasBackdrop=${!backdropUrl.isNullOrBlank()} " +
        "hasDesc=${description.isNotBlank()} hasOverview=${!overviewEn.isNullOrBlank()} hasTmdbPoster=${!tmdbPosterUrl.isNullOrBlank()} " +
        "tmdbTitle=${tmdbTitle.orEmpty()} desc=${description.take(120)} overview=${overviewEn.orEmpty().take(120)} " +
        "image=${imageUrl.take(120)} poster=${tmdbPosterUrl.orEmpty().take(120)} backdrop=${backdropUrl.orEmpty().take(120)} " +
        "rating=$voteAverage runtime=$runtimeMinutes genres=${genres.joinToString("|").take(120)}"
}

private fun CatalogItem?.richestTmdbItem(vararg others: CatalogItem?): CatalogItem? {
    return (listOf(this) + others).filterNotNull().maxByOrNull { it.tmdbScore() }
}

private fun CatalogItem.tmdbScore(): Int {
    return listOf(
        !backdropUrl.isNullOrBlank(),
        description.isNotBlank(),
        !overviewEn.isNullOrBlank(),
        !tmdbPosterUrl.isNullOrBlank(),
        !tmdbTitle.isNullOrBlank(),
        !seriesName.isNullOrBlank(),
    ).count { it }
}
