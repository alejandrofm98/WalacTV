package com.example.walactv

import android.content.Context

class CredentialStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasCredentials(): Boolean {
        return username().isNotBlank() && password().isNotBlank()
    }

    fun username(): String {
        return preferences.getString(KEY_USERNAME, "").orEmpty()
    }

    fun password(): String {
        return preferences.getString(KEY_PASSWORD, "").orEmpty()
    }

    fun save(username: String, password: String) {
        preferences.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "credential_store"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }
}
