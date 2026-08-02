package com.example.walactv.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.util.normalizeLanguageCode
import org.json.JSONObject

object PreferencesManager {
    private const val PREFS_NAME = "walactv_prefs"
    private const val KEY_PREFERRED_LANGUAGE = "preferred_language"
    private const val KEY_PLAYBACK_TRACK_PREFIX = "playback_tracks:"
    private val SUPPORTED_LANGUAGES = setOf("ES", "EN")

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var preferredLanguage: String?
        get() = normalizePreferredLanguage(prefs?.getString(KEY_PREFERRED_LANGUAGE, null))
        set(value) {
            prefs?.edit { putString(KEY_PREFERRED_LANGUAGE, normalizePreferredLanguage(value)) }
        }

    fun getPreferredLanguageOrDefault(): String {
        return normalizePreferredLanguage(preferredLanguage)
    }

    fun normalizePreferredLanguage(value: String?): String {
        val normalized = normalizeLanguageCode(value)
        return if (normalized in SUPPORTED_LANGUAGES) normalized else "ES"
    }

    fun playbackPreferenceKey(
        kind: ContentKind,
        contentId: String,
        episode: CatalogItem? = null,
        username: String = CredentialStore.username(),
    ): String {
        val identity = if (kind == ContentKind.SERIES) {
            episode?.seriesProviderId
                ?: episode?.seriesKey
                ?: episode?.seriesName
                ?: contentId
        } else {
            contentId
        }
        return "$username|${kind.name}|$identity"
    }

    fun getPlaybackTrackPreference(key: String): PlaybackTrackPreference? {
        val raw = prefs?.getString(KEY_PLAYBACK_TRACK_PREFIX + key, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            PlaybackTrackPreference(
                audioLanguage = json.optString("audioLanguage").ifBlank { null },
                audioLabel = json.optString("audioLabel").ifBlank { null },
                subtitleLanguage = json.optString("subtitleLanguage").ifBlank { null },
                subtitleLabel = json.optString("subtitleLabel").ifBlank { null },
                subtitlesDisabled = if (json.has("subtitlesDisabled")) {
                    json.getBoolean("subtitlesDisabled")
                } else {
                    null
                },
            )
        }.getOrNull()
    }

    fun savePlaybackTrackPreference(key: String, preference: PlaybackTrackPreference) {
        val json = JSONObject().apply {
            preference.audioLanguage?.let { put("audioLanguage", normalizeLanguageCode(it)) }
            preference.audioLabel?.let { put("audioLabel", it) }
            preference.subtitleLanguage?.let { put("subtitleLanguage", normalizeLanguageCode(it)) }
            preference.subtitleLabel?.let { put("subtitleLabel", it) }
            preference.subtitlesDisabled?.let { put("subtitlesDisabled", it) }
        }
        prefs?.edit { putString(KEY_PLAYBACK_TRACK_PREFIX + key, json.toString()) }
    }

    fun updatePlaybackTrackPreference(
        key: String,
        transform: (PlaybackTrackPreference) -> PlaybackTrackPreference,
    ) {
        savePlaybackTrackPreference(key, transform(getPlaybackTrackPreference(key) ?: PlaybackTrackPreference()))
    }

    fun clearPlaybackTrackPreference(key: String) {
        prefs?.edit { remove(KEY_PLAYBACK_TRACK_PREFIX + key) }
    }
}

data class PlaybackTrackPreference(
    val audioLanguage: String? = null,
    val audioLabel: String? = null,
    val subtitleLanguage: String? = null,
    val subtitleLabel: String? = null,
    val subtitlesDisabled: Boolean? = null,
)

fun isAudioSelectorEnabled(audioTrackCount: Int): Boolean = audioTrackCount > 1
