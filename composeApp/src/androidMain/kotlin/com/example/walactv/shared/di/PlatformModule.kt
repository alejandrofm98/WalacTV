package com.example.walactv.shared.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.Settings
import com.example.walactv.shared.domain.StubVideoPlayer
import com.example.walactv.shared.domain.VideoPlayer
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent

actual val platformModule = module {
    single<VideoPlayer> { StubVideoPlayer() }
}

actual fun createPlatformSettings(): Settings {
    val context: Context = KoinJavaComponent.get(Context::class.java)
    val prefs = getEncryptedPrefs(context)
    return object : Settings {
        override val keys: Set<String> get() = prefs.all.keys.map { it as String }.toSet()
        override val size: Int get() = prefs.all.size
        override fun getString(key: String, defaultValue: String): String =
            prefs.getString(key, defaultValue) ?: defaultValue
        override fun getStringOrNull(key: String): String? = prefs.getString(key, null)
        override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
        override fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)
        override fun getIntOrNull(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null
        override fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
        override fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)
        override fun getLongOrNull(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null
        override fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }
        override fun getFloat(key: String, defaultValue: Float): Float = prefs.getFloat(key, defaultValue)
        override fun getFloatOrNull(key: String): Float? = if (prefs.contains(key)) prefs.getFloat(key, 0f) else null
        override fun putFloat(key: String, value: Float) { prefs.edit().putFloat(key, value).apply() }
        override fun getDouble(key: String, defaultValue: Double): Double = prefs.getFloat(key, defaultValue.toFloat()).toDouble()
        override fun getDoubleOrNull(key: String): Double? = if (prefs.contains(key)) prefs.getFloat(key, 0f).toDouble() else null
        override fun putDouble(key: String, value: Double) { prefs.edit().putFloat(key, value.toFloat()).apply() }
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)
        override fun getBooleanOrNull(key: String): Boolean? = if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        override fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
        override fun remove(key: String) { prefs.edit().remove(key).apply() }
        override fun hasKey(key: String): Boolean = prefs.contains(key)
        override fun clear() { prefs.edit().clear().apply() }
    }
}

private fun getEncryptedPrefs(context: Context): SharedPreferences {
    return try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "walactv_encrypted_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Exception) {
        context.getSharedPreferences("walactv_settings", Context.MODE_PRIVATE)
    }
}
