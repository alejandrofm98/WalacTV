package com.example.walactv.shared.data

import com.example.walactv.shared.domain.normalizeLanguageCode
import com.russhwolf.settings.Settings

class PreferencesManager(private val settings: Settings) {

    var preferredLanguage: String?
        get() = normalizePreferredLanguage(settings.getStringOrNull(KEY_PREFERRED_LANGUAGE))
        set(value) {
            settings.putString(KEY_PREFERRED_LANGUAGE, normalizePreferredLanguage(value) ?: "ES")
        }

    fun getPreferredLanguageOrDefault(): String {
        return normalizePreferredLanguage(preferredLanguage) ?: "ES"
    }

    private fun normalizePreferredLanguage(value: String?): String? {
        val normalized = normalizeLanguageCode(value)
        return if (normalized in SUPPORTED_LANGUAGES) normalized else "ES"
    }

    companion object {
        private const val KEY_PREFERRED_LANGUAGE = "preferred_language"
        private val SUPPORTED_LANGUAGES = setOf("ES", "EN")
    }
}

private fun Settings.getStringOrNull(key: String): String? {
    val value = getString(key, "")
    return value.ifBlank { null }
}
