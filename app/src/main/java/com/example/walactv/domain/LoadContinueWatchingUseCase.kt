package com.example.walactv.domain

import com.example.walactv.data.model.WatchProgressItem
import com.example.walactv.data.remote.repository.WatchProgressRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadContinueWatchingUseCase @Inject constructor(
    private val watchProgressRepo: WatchProgressRepository,
) {
    suspend operator fun invoke(): Result<List<WatchProgressItem>> {
        return watchProgressRepo.getContinueWatching()
    }
}
