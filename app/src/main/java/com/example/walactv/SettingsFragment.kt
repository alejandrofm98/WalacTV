package com.example.walactv

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SettingsFragment : Fragment() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: IptvRepository
    private lateinit var infoView: TextView
    private lateinit var buttonView: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = IptvRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        infoView = view.findViewById(R.id.settings_last_update)
        buttonView = view.findViewById(R.id.settings_refresh_button)

        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                parentFragmentManager.popBackStack()
                true
            } else {
                false
            }
        }

        buttonView.setOnClickListener { refreshPlaylist() }
        renderLastUpdate()
    }

    private fun refreshPlaylist() {
        buttonView.isEnabled = false
        infoView.text = getString(R.string.settings_refreshing)
        scope.launch {
            runCatching { repository.refreshPlaylistNow() }
                .onSuccess {
                    parentFragmentManager.setFragmentResult(REQUEST_KEY, bundleOf(KEY_REFRESHED to true))
                    renderLastUpdate()
                    Toast.makeText(requireContext(), R.string.settings_refresh_done, Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    infoView.text = getString(R.string.settings_refresh_failed, it.message ?: "sin detalle")
                }
            buttonView.isEnabled = true
        }
    }

    private fun renderLastUpdate() {
        val lastUpdated = repository.getLastPlaylistUpdateMillis()
        infoView.text = if (lastUpdated == 0L) {
            getString(R.string.settings_never_updated)
        } else {
            getString(R.string.settings_last_update_value, formatElapsed(lastUpdated))
        }
    }

    private fun formatElapsed(lastUpdated: Long): String {
        val elapsed = System.currentTimeMillis() - lastUpdated
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
        val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
        val days = TimeUnit.MILLISECONDS.toDays(elapsed)
        return when {
            days > 0 -> getString(R.string.settings_elapsed_days, days)
            hours > 0 -> getString(R.string.settings_elapsed_hours, hours)
            else -> getString(R.string.settings_elapsed_minutes, minutes.coerceAtLeast(1))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        const val REQUEST_KEY = "settings_request"
        const val KEY_REFRESHED = "refreshed"
    }
}
