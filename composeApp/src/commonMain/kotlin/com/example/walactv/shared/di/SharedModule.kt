package com.example.walactv.shared.di

import com.example.walactv.shared.data.ChannelStateStore
import com.example.walactv.shared.data.CredentialStore
import com.example.walactv.shared.data.IptvRepository
import com.example.walactv.shared.data.PreferencesManager
import com.example.walactv.shared.data.TokenStore
import com.example.walactv.shared.data.WatchProgressRepository
import com.example.walactv.shared.network.IptvApiClient
import com.example.walactv.shared.network.createHttpClient
import com.russhwolf.settings.Settings
import org.koin.core.qualifier.named
import org.koin.dsl.module

val IPTV_BASE_URL = named("iptv_base_url")

val sharedModule = module {
    single<Settings> { createPlatformSettings() }
    single { TokenStore(get()) }
    single { CredentialStore(get()) }
    single { PreferencesManager(get()) }
    single { ChannelStateStore(get()) }

    single(named("iptv_base_url")) { "" }

    single {
        val tokenStore = get<TokenStore>()
        createHttpClient(
            baseUrl = get<String>(named("iptv_base_url")),
            authTokenProvider = { tokenStore.get() },
            enableLogging = false,
        )
    }

    single {
        IptvApiClient(get(), get<String>(named("iptv_base_url")))
    }

    single { IptvRepository(get(), get(), get(), get(), get<String>(named("iptv_base_url"))) }
    single { WatchProgressRepository(get()) }
}

val allPlatformModules = listOf(sharedModule, platformModule, databaseModule)

expect val platformModule: org.koin.core.module.Module
expect fun createPlatformSettings(): Settings
