package com.example.walactv.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createPlatformHttpClient(): HttpClient {
    return HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }
}
