package com.example.walactv

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        PreferencesManager.init(this)

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
}
