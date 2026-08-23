package com.example.walactv

import com.example.walactv.data.remote.api.IptvApiService
import com.example.walactv.data.remote.api.dto.CalendarResponse
import com.example.walactv.data.remote.api.dto.CatalogItemDto
import com.example.walactv.data.remote.api.dto.CatalogPageResponse
import com.example.walactv.data.remote.api.dto.ContentStatsResponse
import com.example.walactv.data.remote.api.dto.FilterOptionsResponse
import com.example.walactv.data.remote.api.dto.GenresResponse
import com.example.walactv.data.remote.api.dto.HomeCatalogResponse
import com.example.walactv.data.remote.api.dto.LoginResponse
import com.example.walactv.data.remote.api.dto.PlaybackPreferenceDto
import com.example.walactv.data.remote.api.dto.PlaybackPreferenceUpdateBody
import com.example.walactv.data.remote.api.dto.ReplayListResponse
import com.example.walactv.data.remote.api.dto.SaveWatchProgressBody
import com.example.walactv.data.remote.api.dto.SearchResponse
import com.example.walactv.data.remote.api.dto.SeriesEpisodesResponse

import com.example.walactv.data.remote.api.dto.WatchProgressDto
import com.example.walactv.data.remote.api.dto.WatchProgressListResponse
import com.example.walactv.data.remote.repository.WatchProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

class WatchProgressRepositoryTest {

    private fun errorResponse(code: Int): Response<WatchProgressDto> =
        Response.error(code, "boom".toResponseBody("text/plain".toMediaType()))

    @Test
    fun saveProgress_success_returnsServerBody() = runBlocking {
        val saved = WatchProgressDto(
            contentId = "123",
            contentType = "movie",
            positionMs = 60_000L,
            durationMs = 120_000L,
        )
        val api = FakeApi(saveResult = Response.success(saved))
        val repo = WatchProgressRepository(api)

        val result = repo.saveProgress("123", "movie", 60_000L, 120_000L)

        assertTrue(result.isSuccess)
        assertEquals(saved, result.getOrNull())
        assertEquals(1, api.saveCalls)
    }

    @Test
    fun saveProgress_httpError_isFailure() = runBlocking {
        val api = FakeApi(saveResult = errorResponse(500))
        val repo = WatchProgressRepository(api)

        val result = repo.saveProgress("123", "movie", 60_000L, 120_000L)

        assertTrue(result.isFailure)
        assertEquals(1, api.saveCalls)
    }

    @Test
    fun saveProgress_networkException_isFailure() = runBlocking {
        val api = FakeApi(saveException = java.io.IOException("connection reset"))
        val repo = WatchProgressRepository(api)

        val result = repo.saveProgress("123", "movie", 60_000L, 120_000L)

        assertTrue(result.isFailure)
        assertEquals("connection reset", result.exceptionOrNull()?.message)
    }

    @Test
    fun saveProgress_cancellation_isNotSwallowed() = runBlocking {
        val api = FakeApi(saveException = CancellationException("cancelled"))
        val repo = WatchProgressRepository(api)
        var rethrown: CancellationException? = null

        val job = launch {
            try {
                repo.saveProgress("123", "movie", 60_000L, 120_000L)
                fail("expected CancellationException to be rethrown")
            } catch (e: CancellationException) {
                rethrown = e
            }
        }
        job.join()

        assertEquals("cancelled", rethrown?.message)
    }

    @Test
    fun getProgress_fallsBackToContinueWatching_andNormalizesPrefixedId() = runBlocking {
        val progress = WatchProgressDto(
            contentId = "123",
            contentType = "series",
            positionMs = 300_000L,
            durationMs = 600_000L,
        )
        val api = FakeApi(
            progressResult = errorResponse(404),
            continueWatchingResult = Response.success(WatchProgressListResponse(items = listOf(progress))),
        )
        val repo = WatchProgressRepository(api)

        val result = repo.getProgress("series:123")

        assertEquals(progress, result)
        assertEquals("123", api.lastProgressId)
        assertEquals(1, api.continueWatchingCalls)
    }

    @Test
    fun getProgress_fallsBackByProviderId_forEpisodeFromSeriesDetail() = runBlocking {
        val progress = WatchProgressDto(
            contentId = "episode-uuid",
            providerId = "123",
            contentType = "series",
            positionMs = 300_000L,
            durationMs = 600_000L,
        )
        val api = FakeApi(
            progressResult = errorResponse(404),
            continueWatchingResult = Response.success(WatchProgressListResponse(items = listOf(progress))),
        )
        val repo = WatchProgressRepository(api)

        val result = repo.getProgress("123")

        assertEquals(progress, result)
        assertEquals("123", api.lastProgressId)
        assertEquals(1, api.continueWatchingCalls)
    }

    private class FakeApi(
        private val saveResult: Response<WatchProgressDto>? = null,
        private val saveException: Exception? = null,
        private val progressResult: Response<WatchProgressDto> = Response.success(null),
        private val continueWatchingResult: Response<WatchProgressListResponse> = Response.success(WatchProgressListResponse()),
    ) : IptvApiService {

        var saveCalls = 0
            private set
        var lastProgressId: String? = null
            private set
        var continueWatchingCalls = 0
            private set

        private fun todo(): Nothing = throw NotImplementedError()

        override suspend fun saveWatchProgress(
            id: String,
            body: SaveWatchProgressBody,
        ): Response<WatchProgressDto> {
            saveCalls++
            saveException?.let { throw it }
            return saveResult!!
        }

        override suspend fun login(username: String, password: String): Response<LoginResponse> = todo()
        override suspend fun getHomeCatalog(country: String?): Response<HomeCatalogResponse> = todo()
        override suspend fun getCalendarEvents(date: String, password: String?, client: String?): Response<CalendarResponse> = todo()
        override suspend fun getReplays(eventType: String?, search: String?, page: Int?, pageSize: Int?): Response<ReplayListResponse> = todo()
        override suspend fun getCatalogPage(
            contentType: String,
            country: String?,
            group: String?,
            year: Int?,
            section: String?,
            search: String?,
            genre: String?,
            page: Int,
            pageSize: Int,
        ): Response<CatalogPageResponse> = todo()
        override suspend fun getContentItem(kind: String, id: String): Response<CatalogItemDto> = todo()
        override suspend fun getCountries(contentType: String): Response<FilterOptionsResponse> = todo()
        override suspend fun getGroups(contentType: String, countries: String?, country: String?): Response<FilterOptionsResponse> = todo()
        override suspend fun getGenres(contentType: String): Response<GenresResponse> = todo()
        override suspend fun getContentStats(contentType: String): Response<ContentStatsResponse> = todo()
        override suspend fun getSeriesEpisodes(name: String, page: Int, pageSize: Int, password: String?): Response<SeriesEpisodesResponse> = todo()
        override suspend fun getSeriesEpisodesById(seriesId: String, page: Int, pageSize: Int, password: String?): Response<SeriesEpisodesResponse> = todo()
        override suspend fun search(query: String, page: Int, pageSize: Int, types: String?, password: String?): Response<SearchResponse> = todo()
        override suspend fun getFavorites(): Response<List<CatalogItemDto>> = todo()
        override suspend fun addFavorite(channelId: String): Response<Unit> = todo()
        override suspend fun removeFavorite(channelId: String): Response<Unit> = todo()
        override suspend fun getWatchProgress(limit: Int): Response<WatchProgressListResponse> {
            continueWatchingCalls++
            return continueWatchingResult
        }
        override suspend fun getHomeContinueWatching(limit: Int): Response<WatchProgressListResponse> = continueWatchingResult
        override suspend fun getWatchProgressItem(id: String): Response<WatchProgressDto> {
            lastProgressId = id
            return progressResult
        }
        override suspend fun deleteWatchProgress(id: String): Response<Unit> = todo()
        override suspend fun markWatched(id: String, season: Int?, episode: Int?, completed: Boolean): Response<WatchProgressDto> = todo()
        override suspend fun getPlaybackPreference(contentType: String, catalogId: String): Response<PlaybackPreferenceDto> = todo()
        override suspend fun updatePlaybackPreference(contentType: String, catalogId: String, body: PlaybackPreferenceUpdateBody): Response<PlaybackPreferenceDto> = todo()
        override suspend fun getWatchedItems(limit: Int, offset: Int): Response<WatchProgressListResponse> = todo()
        override suspend fun getFullChannels(): Response<ResponseBody> = todo()
    }
}
