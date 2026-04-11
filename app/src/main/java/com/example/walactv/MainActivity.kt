package com.example.walactv

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    private var consumeBackKeyUp: Boolean = false
    private var backConsumedByDispatch: Boolean = false
    private var playerHandledKeyDown: Boolean = false

    private val onPlayerClosed: (() -> Unit)? = {
        Log.d(TAG, "onPlayerClosed callback fired")
        val composeFragment = supportFragmentManager
            .findFragmentById(R.id.main_browse_fragment) as? ComposeMainFragment
        composeFragment?.restorePlaybackReturnState()
        composeFragment?.restoreFocusAfterPlayer()
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (backConsumedByDispatch) {
                Log.d(TAG, "backPressedCallback: skipping, already consumed by dispatchKeyEvent")
                backConsumedByDispatch = false
                return
            }
            Log.d(TAG, "backPressedCallback.handleOnBackPressed()")
            if (handleCentralizedBack()) return
            Log.d(TAG, "backPressedCallback: centralized returned false, delegating to system")
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

    @androidx.media3.common.util.UnstableApi
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    Log.d(TAG, "BACK ACTION_DOWN: calling handleCentralizedBack()")
                    consumeBackKeyUp = handleCentralizedBack()
                    backConsumedByDispatch = consumeBackKeyUp
                    Log.d(TAG, "BACK ACTION_DOWN: handled=$consumeBackKeyUp")
                    if (consumeBackKeyUp) return true
                    Log.d(TAG, "BACK ACTION_DOWN: NOT handled, falling through")
                }
                KeyEvent.ACTION_UP -> {
                    if (consumeBackKeyUp) {
                        Log.d(TAG, "BACK ACTION_UP: consuming (was handled on DOWN)")
                        consumeBackKeyUp = false
                        return true
                    }
                    Log.d(TAG, "BACK ACTION_UP: NOT consumed (consumeBackKeyUp=false)")
                }
            }
        }

        val container = findViewById<FrameLayout>(R.id.player_container)
        if (container != null && container.visibility == View.VISIBLE) {
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

        // ── 1. Player visible → preguntar al fragment primero ────────────────
        val container = findViewById<FrameLayout>(R.id.player_container)
        val playerFragment = fragmentManager.findFragmentByTag("player_fragment") as? PlayerFragment
        if (container != null && container.visibility == View.VISIBLE &&
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

        // ── 2. Hay fragmentos en el back stack (Search, SeriesDetail…) → pop ─
        if (fragmentManager.backStackEntryCount > 0) {
            Log.d(TAG, "handleCentralizedBack: popping back stack (count=${fragmentManager.backStackEntryCount})")
            fragmentManager.popBackStack()
            return true
        }

        // ── 3. Sin player ni back stack → gestión de modos de navegación ─────
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