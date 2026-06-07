package com.example.walactv.domain

import com.example.walactv.IptvRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignInUseCase @Inject constructor(
    private val repository: IptvRepository,
) {
    suspend operator fun invoke(username: String, password: String): Result<Unit> {
        return runCatching { repository.signIn(username, password) }
    }
}
