package com.example.walactv.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor() : Interceptor {

    @Volatile
    var token: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = this.token ?: return chain.proceed(original)
        val authenticated = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authenticated)
    }
}
