package com.example.walactv.shared.data

import com.russhwolf.settings.Settings

class TokenStore(private val settings: Settings) {
    private var memoryToken: String? = null

    fun save(token: String) {
        memoryToken = token
        settings.putString(KEY_TOKEN, token)
    }

    fun get(): String? {
        memoryToken?.let { return it }
        val saved = settings.getStringOrNull(KEY_TOKEN)
        if (!saved.isNullOrBlank()) {
            memoryToken = saved
        }
        return memoryToken
    }

    fun clear() {
        memoryToken = null
        settings.remove(KEY_TOKEN)
    }

    fun hasToken(): Boolean = get() != null

    private companion object {
        private const val KEY_TOKEN = "jwt_access_token"
    }
}
