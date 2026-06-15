package com.example.walactv.shared.di

import com.example.walactv.shared.data.ContentCacheManager
import com.example.walactv.shared.data.CredentialStore
import com.example.walactv.shared.local.ContentDatabase
import com.example.walactv.shared.local.createSqlDriver
import org.koin.dsl.module

val databaseModule = module {
    single { createSqlDriver() }
    single { ContentDatabase(get()) }
    single {
        ContentCacheManager(
            get(),
            get(),
            get<CredentialStore>(),
            get<com.russhwolf.settings.Settings>().getString("iptv_base_url", ""),
        )
    }
}
