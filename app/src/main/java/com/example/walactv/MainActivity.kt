package com.example.walactv

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.walactv.data.preferences.PreferencesManager
import com.example.walactv.ui.fragment.ComposeMainFragment
import com.example.walactv.ui.fragment.PlayerFragment
import com.example.walactv.ui.fragment.SearchFragment
import kotlinx.coroutines.launch

@SuppressLint("UnsafeOptInUsageError")
class MainActivity : FragmentActivity() {

    private var playerHandledKeyDown: Boolean = false

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            Log.v(TAG, "backPressedCallback.handleOnBackPressed()")
            if (handleCentralizedBack()) return
            Log.v(TAG, "backPressedCallback: centralized returned false, delegating to system")
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        PreferencesManager.init(this)
        onBackPressedDispatcher.addCallback(this, backPressedCallback)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, ComposeMainFragment())
                .commitNow()
        }

        // DEBUG (file-gated): reproduccion automatica de Jackass/Carrera de
        // bestias con fallback real de fuentes para verificar el flujo torrent.
        if (java.io.File("/sdcard/jackass_auto").exists() || java.io.File("/sdcard/bestias_auto").exists() ||
            java.io.File("/sdcard/mid_auto").exists()
        ) {
            lifecycleScope.launch {
                val repo = (application as WalacApp).appComponent.iptvRepository
                if (!repo.hasStoredCredentials()) {
                    try {
                        Log.d(TAG, "auto-login prueba/prueba")
                        repo.signIn("prueba", "prueba")
                    } catch (e: Exception) {
                        Log.e(TAG, "auto-login fallo", e)
                    }
                }
                kotlinx.coroutines.delay(2500)
                debugPlayJackass()
            }
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val container = findViewById<FrameLayout>(R.id.player_container)
        if (container != null && container.isVisible) {
            val composeFragment = supportFragmentManager.findFragmentById(R.id.main_browse_fragment) as? ComposeMainFragment
            if (composeFragment != null && composeFragment.composeDialogOpen) {
                Log.d(TAG, "DIALOG_DPAD: keyCode=${event.keyCode} action=${event.action} composeDialogOpen=true -> super.dispatchKeyEvent")
                return super.dispatchKeyEvent(event)
            }

            val playerFragment = supportFragmentManager.findFragmentByTag("player_fragment") as? PlayerFragment
            if (playerFragment != null && playerFragment.isVisible) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    playerHandledKeyDown = playerFragment.dispatchKeyToPlayer(event)
                    if (playerHandledKeyDown) {
                        return true
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    if (playerHandledKeyDown) {
                        playerHandledKeyDown = false
                        return true
                    }
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_MENU,
                        KeyEvent.KEYCODE_BOOKMARK,
                        KeyEvent.KEYCODE_GUIDE,
                        KeyEvent.KEYCODE_INFO,
                            -> return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleCentralizedBack(): Boolean {
        val fragmentManager = supportFragmentManager
        Log.d(TAG, "handleCentralizedBack: START backStackCount=${fragmentManager.backStackEntryCount}")

        // ── 0. Search visible → cerrar búsqueda ───────────────────────────────
        val searchFragment = fragmentManager.findFragmentById(R.id.main_browse_fragment) as? SearchFragment
        if (searchFragment != null) {
            Log.d(TAG, "handleCentralizedBack: SearchFragment visible, popping")
            fragmentManager.popBackStack()
            return true
        }

        // ── 1. IME abierto → cerrar sin navegar ──────────────────────────────
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val accepting = imm.isAcceptingText()
        val isActive = imm.isActive
        val focusClass = currentFocus?.javaClass?.simpleName
        Log.d(TAG, "handleCentralizedBack: IME check: isAcceptingText=$accepting isActive=$isActive currentFocus=$focusClass")
        if (accepting) {
            imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
            val composeFragment = fragmentManager.findFragmentById(R.id.main_browse_fragment) as? ComposeMainFragment
            composeFragment?.onSearchBackPressed()
            return true
        }

        // ── 2. Player visible → preguntar al fragment primero ────────────────
        val container = findViewById<FrameLayout>(R.id.player_container)
        val playerFragment = fragmentManager.findFragmentByTag("player_fragment") as? PlayerFragment
        if (container != null && container.isVisible &&
            playerFragment != null && playerFragment.isVisible
        ) {
            val menuFocused = playerFragment.isOverlayMenuFocused()
            Log.d(TAG, "handleCentralizedBack: player visible, menuFocused=$menuFocused")

            // Overlay del live TV con menú → cerrar el menú
            if (menuFocused) {
                playerFragment.hideOverlayMenu()
                return true
            }

            // VOD: si el controlador está visible → ocultarlo (como Netflix)
            // Si devuelve false → el controlador ya está oculto, cerrar el player
            if (playerFragment.handleBackPress()) {
                return true
            }

            // Cerrar el player
            playerFragment.closeFromHost()
            val composeFragment = fragmentManager.findFragmentById(R.id.main_browse_fragment) as? ComposeMainFragment
            composeFragment?.restorePlaybackReturnState()
            composeFragment?.restoreFocusAfterPlayer()
            return true
        }

        // ── 3. Hay fragmentos en el back stack (SeriesDetail…) → pop ─────────
        if (fragmentManager.backStackEntryCount > 0) {
            Log.d(TAG, "handleCentralizedBack: popping back stack (count=${fragmentManager.backStackEntryCount})")
            // Set pending flag BEFORE popBackStack — popBackStack is async so
            // findFragmentById immediately after it returns the detail fragment, not
            // ComposeMainFragment. Use filterIsInstance to find the real main fragment.
            val composeFragment = fragmentManager.fragments
                .filterIsInstance<ComposeMainFragment>()
                .firstOrNull()
            if (composeFragment != null) {
                when (composeFragment.currentMode) {
                    ComposeMainFragment.MainMode.Home -> {
                        composeFragment.pendingHomeFocusRestore = true
                        Log.d(TAG, "handleCentralizedBack: set pendingHomeFocusRestore")
                    }
                    ComposeMainFragment.MainMode.TV,
                    ComposeMainFragment.MainMode.Events -> {
                        composeFragment.pendingGuideFocusRestore = true
                        Log.d(TAG, "handleCentralizedBack: set pendingGuideFocusRestore")
                    }
                    ComposeMainFragment.MainMode.Discover -> {
                        composeFragment.pendingDiscoverFocusRestore = true
                        Log.d(TAG, "handleCentralizedBack: set pendingDiscoverFocusRestore")
                    }
                    else -> { /* Settings: no-op */ }
                }
            }
            fragmentManager.popBackStack()
            return true
        }

        // ── 4. Sin player ni back stack → gestión de modos de navegación ─────
        val composeFragment = fragmentManager.findFragmentById(R.id.main_browse_fragment) as? ComposeMainFragment
            ?: run {
                Log.d(TAG, "handleCentralizedBack: no ComposeMainFragment found")
                return false
            }

        val currentMode = composeFragment.currentNavigationMode()
        Log.d(TAG, "handleCentralizedBack: currentMode=$currentMode")

        return when (currentMode) {
            "Home" -> {
                Log.d(TAG, "handleCentralizedBack: already Home, letting system handle (exit app)")
                false
            }
            else -> {
                Log.d(TAG, "handleCentralizedBack: navigating to Home from $currentMode")
                composeFragment.navigateToHome()
                true
            }
        }
    }

    /**
     * DEBUG: abre el player con la misma logica de fuentes que produccion
     * (torrent mejor sembrado + fallback via onSelectUnifiedOption que
     * reemplaza el fragment) para verificar en logcat el flujo completo.
     */
    private fun debugPlayJackass() {
        // Dos triggers: jackass_auto (desde 0) y bestias_auto (a mitad).
        val bestias = java.io.File("/sdcard/bestias_auto").exists()
        val mid = java.io.File("/sdcard/mid_auto").exists()
        val imdb = if (bestias) "tt32358025" else "tt39316472"
        val contentId = if (bestias) "movie:1d515951-ed07-4b67-bc89-fa043e4e2350" else "movie:035f6aac-a9a7-42d4-a834-4562686fefad"
        val title = if (bestias) "Carrera de bestias" else "Jackass: Lo mejor para el final"
        val startPos = when {
            mid -> 600_000L           // 10min: mitad, torrent AVI decodificable
            bestias -> 620_000L
            else -> 0L
        }
        val forceHash = if (mid) "eb4db2806df413c3ca1013b83f713a0c341a5277" else null
        Log.d(TAG, "debugPlayJackass: iniciando imdb=$imdb pos=$startPos forceHash=$forceHash")
        val repo = (application as WalacApp).appComponent.iptvRepository
        lifecycleScope.launch {
            try {
                val all = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    repo.getTorrentioMovieStreams(imdb)
                }
                val streams = (if (forceHash != null) {
                    val forced = all.filter { it.infoHash == forceHash }
                    (forced + all.filterNot { it.infoHash == forceHash })
                } else all).bestTorrentFirstForDebug()
                Log.d(TAG, "debugPlayJackass: ${streams.size} fuentes: ${streams.map { it.infoHash?.take(8) to it.seeders }}")
                if (streams.isEmpty()) return@launch
                openDebugPlayer(streams, 0, startPos, contentId, title)
            } catch (e: Exception) {
                Log.e(TAG, "debugPlayJackass error", e)
            }
        }
    }

    private fun openDebugPlayer(
        streams: List<com.example.walactv.data.model.StreamOption>,
        index: Int,
        positionMs: Long,
        contentId: String,
        title: String,
    ) {
        val stream = streams[index]
        Log.d(TAG, "openDebugPlayer: idx=$index hash=${stream.infoHash} pos=$positionMs")
        val fragment = PlayerFragment().apply {
            initialize(
                streamUrl = stream.url,
                overlayNumber = "",
                overlayTitle = title,
                overlayMeta = "debug s${stream.seeders ?: 0} ${stream.quality ?: ""}",
                contentKind = com.example.walactv.data.model.ContentKind.MOVIE,
                onNavigateChannel = {}, onNavigateOption = {}, onDirectChannelNumber = { false },
                onToggleFavorite = { false }, onOpenFavorites = { false }, onOpenRecents = { false },
                contentId = contentId,
                positionMs = positionMs,
                overlayBackdropUrl = "https://image.tmdb.org/t/p/w1280/dUbP1HNdI0aCq1zgRJw28PWSqmk.jpg",
                streamOptionLabels = streams.map { it.label },
                currentOptionIndex = index,
                unifiedStreamOptions = streams.map { s ->
                    com.example.walactv.data.model.UnifiedStreamOption(
                        s.language ?: "ES", s.language ?: "ES", s.quality ?: "HD",
                        s.url, s.providerId, s.headers,
                    )
                },
                onSelectUnifiedOption = { nextIdx, resumeMs ->
                    Log.d(TAG, "onSelectUnifiedOption fallback: $index -> $nextIdx pos=$resumeMs")
                    openDebugPlayer(streams, nextIdx, resumeMs, contentId, title)
                },
            )
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.player_container, fragment, "player_fragment")
            .commitNow()
        findViewById<FrameLayout>(R.id.player_container)?.visibility = android.view.View.VISIBLE
    }

    private fun List<com.example.walactv.data.model.StreamOption>.bestTorrentFirstForDebug():
        List<com.example.walactv.data.model.StreamOption> =
        sortedWith(
            compareByDescending<com.example.walactv.data.model.StreamOption> { it.seeders ?: 0 }
                .thenByDescending { it.sizeBytes ?: 0L },
        )

    companion object {
        private const val TAG = "MainActivity"
    }
}