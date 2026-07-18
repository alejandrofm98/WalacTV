package com.example.walactv.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object CredentialStore {

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun hasCredentials(): Boolean {
        return username().isNotBlank() && password().isNotBlank()
    }

    fun username(): String {
        return prefs.getString(KEY_USERNAME, "").orEmpty()
    }

    fun password(): String {
        return prefs.getString(KEY_PASSWORD, "").orEmpty()
    }

    fun save(username: String, password: String) {
        prefs.edit {
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
        }
    }

    fun clear() {
        prefs.edit {
            remove(KEY_USERNAME)
            remove(KEY_PASSWORD)
        }
    }

    private const val PREFS_NAME = "credential_store"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
}
