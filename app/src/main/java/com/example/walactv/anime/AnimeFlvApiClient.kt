package com.example.walactv.anime

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object AnimeFlvApiClient {

    private const val TAG = "AnimeFlvApiClient"
    private const val BASE_URL = "https://animeflv.ahmedrangel.com"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Android TV) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: AnimeFlvApiService by lazy {
        retrofit.create(AnimeFlvApiService::class.java)
    }

    // ── Public API ───────────────────────────────────────────────────────────

    suspend fun getAnimesOnAir(): Result<List<AnimeOnAir>> = runCatching {
        val response = service.getAnimesOnAir()
        response.data
    }

    suspend fun getLatestEpisodes(): Result<List<LatestEpisode>> = runCatching {
        val response = service.getLatestEpisodes()
        response.data
    }

    suspend fun getAnimeDetail(slug: String): Result<AnimeDetail> = runCatching {
        val response = service.getAnimeDetail(slug)
        response.data
    }

    suspend fun getEpisodeServers(slug: String, number: Int): Result<EpisodeDetail> = runCatching {
        val response = service.getEpisodeServers(slug, number)
        response.data
    }

    suspend fun search(query: String, page: Int? = null): Result<SearchResult> = runCatching {
        val response = service.search(query, page)
        response.data
    }
}
