package com.example.walactv

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity

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
            Log.d("DiscoverFocus", "backStack pop: backStackEntryCount=${fragmentManager.backStackEntryCount}")
            val composeFragment = fragmentManager.findFragmentById(R.id.main_browse_fragment) as? ComposeMainFragment
            Log.d("DiscoverFocus", "backStack pop: composeFragment found=${composeFragment != null} contentFocusTrigger=${composeFragment?.contentFocusTrigger} discoverFocusedId=${composeFragment?.discoverFocusedItemStableId}")
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

    companion object {
        private const val TAG = "MainActivity"
    }
}