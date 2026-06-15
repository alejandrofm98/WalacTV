package com.example.walactv.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun createPlatformHttpClient(): HttpClient

fun createHttpClient(
    baseUrl: String,
    authTokenProvider: () -> String?,
    enableLogging: Boolean = false,
): HttpClient {
    return createPlatformHttpClient().config {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
        install(Auth) {
            bearer {
                loadTokens {
                    val token = authTokenProvider()
                    if (token != null) BearerTokens(token, "") else null
                }
                sendWithoutRequest { request ->
                    !request.url.toString().contains("/api/auth/login")
                }
            }
        }
        if (enableLogging) {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        println("Ktor: $message")
                    }
                }
                level = LogLevel.BODY
            }
        }
    }
}
