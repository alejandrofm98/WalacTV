package com.example.walactv.anime

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AnimeFlvApiService {

    // ── List endpoints ───────────────────────────────────────────────────────

    @GET("/api/list/animes-on-air")
    suspend fun getAnimesOnAir(): AnimeFlvResponse<List<AnimeOnAir>>

    @GET("/api/list/latest-episodes")
    suspend fun getLatestEpisodes(): AnimeFlvResponse<List<LatestEpisode>>

    // ── Anime endpoints ──────────────────────────────────────────────────────

    @GET("/api/anime/{slug}")
    suspend fun getAnimeDetail(@Path("slug") slug: String): AnimeFlvResponse<AnimeDetail>

    @GET("/api/anime/{slug}/episode/{number}")
    suspend fun getEpisodeServers(
        @Path("slug") slug: String,
        @Path("number") number: Int,
    ): AnimeFlvResponse<EpisodeDetail>

    @GET("/api/anime/episode/{slug}")
    suspend fun getEpisodeBySlug(@Path("slug") slug: String): AnimeFlvResponse<EpisodeDetail>

    // ── Search endpoints ─────────────────────────────────────────────────────

    @GET("/api/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("page") page: Int? = null,
    ): AnimeFlvResponse<SearchResult>

    @POST("/api/search/by-filter")
    suspend fun searchByFilter(
        @Body filter: AnimeFilter,
        @Query("order") order: String? = null,
        @Query("page") page: Int? = null,
    ): AnimeFlvResponse<SearchResult>
}

data class AnimeFilter(
    val types: List<String>? = null,
    val genres: List<String>? = null,
    val statuses: List<Int>? = null,
)
