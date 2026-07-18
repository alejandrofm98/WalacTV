package com.example.walactv.domain

import com.example.walactv.data.remote.api.dto.WatchProgressDto
import com.example.walactv.data.remote.repository.WatchProgressRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadContinueWatchingUseCase @Inject constructor(
    private val watchProgressRepo: WatchProgressRepository,
) {
    suspend operator fun invoke(): Result<List<WatchProgressDto>> {
        return watchProgressRepo.getContinueWatching()
    }
}
