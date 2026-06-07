package com.example.walactv

import android.app.Application
import com.example.walactv.local.ContentCacheManager
import com.example.walactv.network.AuthInterceptor
import com.example.walactv.network.IptvApiService
import com.example.walactv.network.NetworkModule
import com.example.walactv.viewmodel.HomeViewModel
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

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance context: android.content.Context): AppComponent
    }
}
