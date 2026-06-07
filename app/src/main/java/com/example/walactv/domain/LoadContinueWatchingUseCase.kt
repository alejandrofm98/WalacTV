package com.example.walactv.domain

import com.example.walactv.WatchProgressItem
import com.example.walactv.WatchProgressRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadContinueWatchingUseCase @Inject constructor(
    private val watchProgressRepo: WatchProgressRepository,
) {
    suspend operator fun invoke(): Result<List<WatchProgressItem>> {
        return runCatching { watchProgressRepo.getContinueWatching() }
    }
}
