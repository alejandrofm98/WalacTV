package com.example.walactv.shared.di

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import com.example.walactv.shared.data.DesktopVideoPlayer
import com.example.walactv.shared.domain.VideoPlayer
import org.koin.dsl.module
import java.util.prefs.Preferences

actual val platformModule = module {
    single<VideoPlayer> { DesktopVideoPlayer() }
}

actual fun createPlatformSettings(): Settings {
    return PreferencesSettings(Preferences.userRoot().node("walactv"))
}
