package com.example.walactv.di

import android.app.Application
import com.example.walactv.data.preferences.ChannelStateStore
import com.example.walactv.data.remote.api.AuthInterceptor
import com.example.walactv.data.remote.api.IptvApiService
import com.example.walactv.data.remote.api.NetworkModule
import com.example.walactv.data.remote.repository.IntroDbRepository
import com.example.walactv.data.remote.repository.IptvRepository
import com.example.walactv.data.remote.repository.WatchProgressRepository
import com.example.walactv.local.ContentCacheManager
import com.example.walactv.ui.viewmodel.HomeViewModel
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [NetworkModule::class])
interface AppComponent {
    val apiService: IptvApiService
    val authInterceptor: AuthInterceptor
    val watchProgressRepository: WatchProgressRepository
    val iptvRepository: IptvRepository
    val contentCacheManager: ContentCacheManager
    val channelStateStore: ChannelStateStore
    val homeViewModel: HomeViewModel
    val introDbRepository: IntroDbRepository

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance context: android.content.Context): AppComponent
    }
}
