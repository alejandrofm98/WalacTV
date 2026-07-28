package com.example.walactv.data.remote.api

import com.example.walactv.data.remote.api.dto.CatalogItemDto
import com.example.walactv.data.remote.api.dto.CatalogPageResponse
import com.example.walactv.data.remote.api.dto.FilterOptionsResponse
import com.example.walactv.data.remote.api.dto.GenresResponse
import com.example.walactv.data.remote.api.dto.HomeCatalogResponse
import com.example.walactv.data.remote.api.dto.LoginResponse
import com.example.walactv.data.remote.api.dto.CalendarResponse
import com.example.walactv.data.remote.api.dto.SearchResponse
import com.example.walactv.data.remote.api.dto.SeriesEpisodesResponse
import com.example.walactv.data.remote.api.dto.ContentStatsResponse
import com.example.walactv.data.remote.api.dto.ReplayListResponse
import com.example.walactv.data.remote.api.dto.WatchProgressListResponse
import com.example.walactv.data.remote.api.dto.WatchProgressDto
import com.example.walactv.data.remote.api.dto.SaveWatchProgressBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface IptvApiService {

    // Auth
    @POST("api/auth/login")
    @FormUrlEncoded
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
    ): Response<LoginResponse>

    // Home
    @GET("api/home")
    suspend fun getHomeCatalog(
        @Query("country") country: String? = null,
    ): Response<HomeCatalogResponse>

    @GET("api/calendar/{date}")
    suspend fun getCalendarEvents(
        @Path("date") date: String,
        @Query("password") password: String? = null,
        @Query("client") client: String? = null,
    ): Response<CalendarResponse>

    // Replays (UFC)
    @GET("api/replays")
    suspend fun getReplays(
        @Query("event_type") eventType: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
    ): Response<ReplayListResponse>

    // Content
    @GET("api/content")
    suspend fun getCatalogPage(
        @Query("content_type") contentType: String,
        @Query("country") country: String? = null,
        @Query("group") group: String? = null,
        @Query("year") year: Int? = null,
        @Query("section_title") section: String? = null,
        @Query("search") search: String? = null,
        @Query("genre") genre: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 24,
    ): Response<CatalogPageResponse>

    @GET("api/content/{kind}/{id}")
    suspend fun getContentItem(
        @Path("kind") kind: String,
        @Path("id") id: String,
    ): Response<CatalogItemDto>

    @GET("api/content/countries")
    suspend fun getCountries(
        @Query("content_type") contentType: String,
    ): Response<FilterOptionsResponse>

    @GET("api/content/groups")
    suspend fun getGroups(
        @Query("content_type") contentType: String,
        @Query("countries") countries: String? = null,
        @Query("country") country: String? = null,
    ): Response<FilterOptionsResponse>

    @GET("api/content/genres")
    suspend fun getGenres(
        @Query("content_type") contentType: String,
    ): Response<GenresResponse>

    @GET("api/content/stats")
    suspend fun getContentStats(
        @Query("content_type") contentType: String,
    ): Response<ContentStatsResponse>

    // Series
    @GET("api/series/{name}/episodes")
    suspend fun getSeriesEpisodes(
        @Path("name") name: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
        @Query("password") password: String? = null,
    ): Response<SeriesEpisodesResponse>

    @GET("api/series/by-id/{series_id}/episodes")
    suspend fun getSeriesEpisodesById(
        @Path("series_id") seriesId: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
        @Query("password") password: String? = null,
    ): Response<SeriesEpisodesResponse>

    // Search
    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 50,
        @Query("types") types: String? = null,
        @Query("password") password: String? = null,
    ): Response<SearchResponse>

    // Favorites
    @GET("api/channel-favorites")
    suspend fun getFavorites(): Response<List<CatalogItemDto>>

    @POST("api/channel-favorites")
    @FormUrlEncoded
    suspend fun addFavorite(
        @Field("channel_id") channelId: String,
    ): Response<Unit>

    @DELETE("api/channel-favorites/{channelId}")
    suspend fun removeFavorite(
        @Path("channelId") channelId: String,
    ): Response<Unit>

    // Watch Progress
    @GET("api/watch-progress")
    suspend fun getWatchProgress(
        @Query("limit") limit: Int = 20,
    ): Response<WatchProgressListResponse>

    @GET("api/watch-progress/{id}")
    suspend fun getWatchProgressItem(
        @Path("id") id: String,
    ): Response<WatchProgressDto>

    @PUT("api/watch-progress/{id}")
    suspend fun saveWatchProgress(
        @Path("id") id: String,
        @Body body: SaveWatchProgressBody,
    ): Response<WatchProgressDto>

    @DELETE("api/watch-progress/{id}")
    suspend fun deleteWatchProgress(
        @Path("id") id: String,
    ): Response<Unit>

    @POST("api/watch-progress/{id}/mark-watched")
    suspend fun markWatched(
        @Path("id") id: String,
    ): Response<WatchProgressDto>

    @GET("api/watch-progress/watched")
    suspend fun getWatchedItems(
        @Query("limit") limit: Int = 200,
    ): Response<WatchProgressListResponse>

    // Full catalog sync (streaming)
    @GET("api/full/channels")
    suspend fun getFullChannels(): Response<ResponseBody>
}
