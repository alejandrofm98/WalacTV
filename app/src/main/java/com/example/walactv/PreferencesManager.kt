package com.example.walactv

import android.content.Context
import android.content.SharedPreferences

object PreferencesManager {
    private const val PREFS_NAME = "walactv_prefs"
    private const val KEY_PREFERRED_LANGUAGE = "preferred_language"
    private val SUPPORTED_LANGUAGES = setOf("ES", "EN", "LATAM")

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var preferredLanguage: String?
        get() = normalizePreferredLanguage(prefs?.getString(KEY_PREFERRED_LANGUAGE, null))
        set(value) {
            prefs?.edit()?.putString(KEY_PREFERRED_LANGUAGE, normalizePreferredLanguage(value))?.apply()
        }

    fun getPreferredLanguageOrDefault(): String {
        return normalizePreferredLanguage(preferredLanguage)
    }

    fun normalizePreferredLanguage(value: String?): String {
        val normalized = normalizeLanguageCode(value)
        return if (normalized in SUPPORTED_LANGUAGES) normalized else "ES"
    }
}
