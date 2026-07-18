package com.example.walactv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.walactv.data.preferences.ChannelStateStore
import com.example.walactv.data.remote.repository.IptvRepository
import com.example.walactv.data.remote.repository.WatchProgressRepository
import com.example.walactv.local.ContentCacheManager
import javax.inject.Inject

class HomeViewModelFactory @Inject constructor(
    private val repository: IptvRepository,
    private val watchProgressRepo: WatchProgressRepository,
    private val contentCacheManager: ContentCacheManager,
    private val channelStateStore: ChannelStateStore,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(
                repository = repository,
                watchProgressRepo = watchProgressRepo,
                contentCacheManager = contentCacheManager,
                channelStateStore = channelStateStore,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
