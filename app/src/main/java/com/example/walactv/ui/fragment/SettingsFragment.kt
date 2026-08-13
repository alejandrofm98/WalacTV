package com.example.walactv.ui.fragment

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.walactv.R
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.example.walactv.data.remote.repository.IptvRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

        buttonView.setOnClickListener { refreshCatalog() }
        renderLastUpdate()
    }

    private fun refreshCatalog() {
        buttonView.isEnabled = false
        infoView.text = getString(R.string.settings_refreshing)
        scope.launch {
            runCatching { repository.refreshCatalogNow() }
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
