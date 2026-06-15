package com.example.walactv.mobile

import android.app.Application
import com.example.walactv.shared.di.IPTV_BASE_URL
import com.example.walactv.shared.di.allPlatformModules
import com.example.walactv.shared.di.databaseModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.dsl.module

class MobileApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val url = BuildConfig.IPTV_BASE_URL
        val iptvUrl = url.takeIf { it.isNotBlank() && !it.contains("example.invalid") }
            ?: "http://localhost:3010"

        startKoin {
            androidLogger()
            androidContext(this@MobileApp)
            modules(allPlatformModules + databaseModule + module {
                single(IPTV_BASE_URL) { iptvUrl }
            })
        }
    }
}
