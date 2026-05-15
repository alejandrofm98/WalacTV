package com.example.walactv

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialStore(context: Context) {

    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

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
