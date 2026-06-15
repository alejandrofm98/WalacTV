package com.example.walactv.shared.data

import com.russhwolf.settings.Settings

class CredentialStore(private val settings: Settings) {

    fun hasCredentials(): Boolean {
        return username().isNotBlank() && password().isNotBlank()
    }

    fun username(): String {
        return settings.getString(KEY_USERNAME, "")
    }

    fun password(): String {
        return settings.getString(KEY_PASSWORD, "")
    }

    fun save(username: String, password: String) {
        settings.putString(KEY_USERNAME, username)
        settings.putString(KEY_PASSWORD, password)
    }

    fun clear() {
        settings.remove(KEY_USERNAME)
        settings.remove(KEY_PASSWORD)
    }

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }
}
