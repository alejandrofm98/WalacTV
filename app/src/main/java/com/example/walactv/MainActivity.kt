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

    /**
     * Intercept key events at the Activity level so that D-pad keys always
     * reach [PlayerFragment] regardless of which child view currently holds
     * focus. This fixes the problem where [PlayerView] steals focus and
     * swallows D-pad events before any OnKeyListener can react.
     */
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
            val guideFragment = supportFragmentManager.findFragmentByTag("guide_fragment")
            if (guideFragment != null && guideFragment.isVisible) {
                return super.dispatchKeyEvent(event)
            }

            val playerFragment = supportFragmentManager.findFragmentByTag("player_fragment") as? PlayerFragment
            if (playerFragment != null && playerFragment.isVisible) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (playerFragment.dispatchKeyToPlayer(event.keyCode)) {
                        return true
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
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
        Log.d(TAG, "handleCentralizedBack: START")

        val guideFragment = fragmentManager.findFragmentByTag("guide_fragment")
        if (guideFragment != null && guideFragment.isVisible) {
            Log.d(TAG, "handleCentralizedBack: GUIDE path")
            fragmentManager.popBackStack()
            return true
        }

        val container = findViewById<FrameLayout>(R.id.player_container)
        val playerFragment = fragmentManager.findFragmentByTag("player_fragment") as? PlayerFragment
        Log.d(TAG, "handleCentralizedBack: container=${container?.visibility}, playerFragment=${playerFragment != null}, playerVisible=${playerFragment?.isVisible}")

        if (container != null && container.visibility == View.VISIBLE && playerFragment != null && playerFragment.isVisible) {
            Log.d(TAG, "handleCentralizedBack: PLAYER path - closing player")
            playerFragment.closeFromHost()
            val composeFragment = fragmentManager.findFragmentById(R.id.main_browse_fragment) as? ComposeMainFragment
            Log.d(TAG, "handleCentralizedBack: composeFragment=${composeFragment != null}")
            composeFragment?.restorePlaybackReturnState()
            composeFragment?.restoreFocusAfterPlayer()
            return true
        }

        Log.d(TAG, "handleCentralizedBack: FALLTHROUGH -> navigateHomeOnBack")
        val rootFragment = fragmentManager.findFragmentById(R.id.main_browse_fragment) as? ComposeMainFragment
        val result = rootFragment?.navigateHomeOnBack() == true
        Log.d(TAG, "handleCentralizedBack: navigateHomeOnBack returned $result")
        return result
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
