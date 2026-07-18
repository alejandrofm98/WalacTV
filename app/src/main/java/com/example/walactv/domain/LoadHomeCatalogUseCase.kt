package com.example.walactv.domain

import com.example.walactv.data.model.HomeCatalog
import com.example.walactv.data.remote.repository.IptvRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadHomeCatalogUseCase @Inject constructor(
    private val repository: IptvRepository,
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): Result<HomeCatalog> {
        return runCatching { repository.loadHomeCatalog(forceRefresh) }
    }
}
