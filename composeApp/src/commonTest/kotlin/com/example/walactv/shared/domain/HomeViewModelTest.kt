package com.example.walactv.shared.domain

import com.example.walactv.shared.data.ChannelStateStore
import com.example.walactv.shared.data.CredentialStore
import com.example.walactv.shared.data.PreferencesManager
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var credentialStore: CredentialStore
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var channelStateStore: ChannelStateStore

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val settings = MapSettings()
        credentialStore = CredentialStore(settings)
        preferencesManager = PreferencesManager(settings)
        channelStateStore = ChannelStateStore(settings)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun credentialStore_initiallyEmpty() {
        assertFalse(credentialStore.hasCredentials())
        assertEquals("", credentialStore.username())
        assertEquals("", credentialStore.password())
    }

    @Test
    fun credentialStore_saveAndRetrieve() {
        credentialStore.save("user@test.com", "password123")

        assertTrue(credentialStore.hasCredentials())
        assertEquals("user@test.com", credentialStore.username())
        assertEquals("password123", credentialStore.password())
    }

    @Test
    fun credentialStore_clearRemovesCredentials() {
        credentialStore.save("user@test.com", "password123")
        credentialStore.clear()

        assertFalse(credentialStore.hasCredentials())
        assertEquals("", credentialStore.username())
        assertEquals("", credentialStore.password())
    }

    @Test
    fun preferencesManager_defaultLanguageIsSpanish() {
        val default = preferencesManager.getPreferredLanguageOrDefault()
        assertEquals("ES", default)
    }

    @Test
    fun preferencesManager_setAndGetPreferredLanguage() {
        preferencesManager.preferredLanguage = "EN"
        assertEquals("EN", preferencesManager.getPreferredLanguageOrDefault())
    }

    @Test
    fun preferencesManager_normalizesInvalidLanguage() {
        preferencesManager.preferredLanguage = "INVALID"
        assertEquals("ES", preferencesManager.getPreferredLanguageOrDefault())
    }

    @Test
    fun catalogItem_stableIdIsUsedAsProvided() {
        val item = createTestCatalogItem(stableId = "custom_123")
        assertEquals("custom_123", item.stableId)
    }

    @Test
    fun catalogItem_streamOptionsEmptyByDefault() {
        val item = createTestCatalogItem()
        assertTrue(item.streamOptions.isEmpty())
    }

    @Test
    fun catalogItem_contentKindEnum() {
        assertEquals(4, ContentKind.entries.size)
        assertTrue(ContentKind.entries.contains(ContentKind.MOVIE))
        assertTrue(ContentKind.entries.contains(ContentKind.SERIES))
        assertTrue(ContentKind.entries.contains(ContentKind.CHANNEL))
        assertTrue(ContentKind.entries.contains(ContentKind.EVENT))
    }

    @Test
    fun catalogItem_isVodContent() {
        val movie = createTestCatalogItem(kind = ContentKind.MOVIE)
        val series = createTestCatalogItem(kind = ContentKind.SERIES)
        val channel = createTestCatalogItem(kind = ContentKind.CHANNEL)

        assertTrue(movie.isVodContent())
        assertTrue(series.isVodContent())
        assertFalse(channel.isVodContent())
    }

    @Test
    fun catalogItem_idiomaFromGroup() {
        val item = createTestCatalogItem(group = "ES | Accion")
        assertEquals("ES", item.idioma)
    }

    @Test
    fun catalogItem_subgrupoFromGroup() {
        val item = createTestCatalogItem(group = "ES | Accion")
        assertEquals("Accion", item.subgrupo)
    }

    @Test
    fun streamOption_toUnifiedOptions() {
        val streamOptions = listOf(
            StreamOption(label = "HD", url = "https://example.com/stream", language = "ES", quality = "HD"),
        )

        val unified = streamOptions.toUnifiedOptions()

        assertEquals(1, unified.size)
        assertEquals("HD", unified[0].quality)
        assertEquals("https://example.com/stream", unified[0].url)
    }

    @Test
    fun streamOption_toUnifiedOptionsSkipsWithoutLanguage() {
        val streamOptions = listOf(
            StreamOption(label = "Directo", url = "https://example.com/stream"),
        )

        val unified = streamOptions.toUnifiedOptions()

        assertTrue(unified.isEmpty())
    }

    @Test
    fun streamOption_toUnifiedOptionsDefaultsToHD() {
        val streamOptions = listOf(
            StreamOption(label = "Directo", url = "https://example.com/stream", language = "ES"),
        )

        val unified = streamOptions.toUnifiedOptions()

        assertEquals(1, unified.size)
        assertEquals("HD", unified[0].quality)
    }

    @Test
    fun streamOption_toUnifiedOptionsSortsByQuality() {
        val streamOptions = listOf(
            StreamOption(label = "SD", url = "https://example.com/sd", language = "ES", quality = "SD"),
            StreamOption(label = "UHD", url = "https://example.com/uhd", language = "ES", quality = "UHD"),
            StreamOption(label = "HD", url = "https://example.com/hd", language = "ES", quality = "HD"),
        )

        val unified = streamOptions.toUnifiedOptions()

        assertEquals(3, unified.size)
        assertEquals("UHD", unified[0].quality)
        assertEquals("HD", unified[1].quality)
        assertEquals("SD", unified[2].quality)
    }

    @Test
    fun streamOption_toUnifiedOptionsDeduplicates() {
        val streamOptions = listOf(
            StreamOption(label = "HD", url = "https://example.com/stream1", language = "ES", quality = "HD"),
            StreamOption(label = "HD", url = "https://example.com/stream2", language = "ES", quality = "HD"),
        )

        val unified = streamOptions.toUnifiedOptions()

        assertEquals(1, unified.size)
    }

    @Test
    fun unifiedStreamOption_displayLabel() {
        val option = UnifiedStreamOption(
            language = "Español",
            quality = "HD",
            url = "https://example.com/stream",
        )

        assertEquals("Español HD", option.displayLabel)
    }

    @Test
    fun browseSection_dataClass() {
        val items = listOf(createTestCatalogItem())
        val section = BrowseSection(title = "Test Section", items = items)

        assertEquals("Test Section", section.title)
        assertEquals(1, section.items.size)
    }

    @Test
    fun homeCatalog_dataClass() {
        val items = listOf(createTestCatalogItem())
        val sections = listOf(BrowseSection("Section", items))
        val catalog = HomeCatalog(sections = sections, searchableItems = items)

        assertEquals(1, catalog.sections.size)
        assertEquals(1, catalog.searchableItems.size)
    }

    @Test
    fun watchProgressItem_progressPercent() {
        val item = WatchProgressItem(
            contentId = "1",
            contentType = "movie",
            positionMs = 50_000,
            durationMs = 100_000,
            normalizedTitle = "Test",
            title = "Test",
            imageUrl = "",
            seriesName = null,
            seasonNumber = null,
            episodeNumber = null,
            lastWatchedAt = "",
        )

        assertEquals(50, item.progressPercent)
    }

    @Test
    fun watchProgressItem_isCompleted() {
        val item = WatchProgressItem(
            contentId = "1",
            contentType = "movie",
            positionMs = 96_000,
            durationMs = 100_000,
            normalizedTitle = "Test",
            title = "Test",
            imageUrl = "",
            seriesName = null,
            seasonNumber = null,
            episodeNumber = null,
            lastWatchedAt = "",
        )

        assertTrue(item.isCompleted)
    }

    @Test
    fun watchProgressItem_shouldRestoreProgress() {
        val item = WatchProgressItem(
            contentId = "1",
            contentType = "movie",
            positionMs = 90_000,
            durationMs = 100_000,
            normalizedTitle = "Test",
            title = "Test",
            imageUrl = "",
            seriesName = null,
            seasonNumber = null,
            episodeNumber = null,
            lastWatchedAt = "",
            isWatched = false,
        )

        assertTrue(item.shouldRestoreProgress)
    }

    @Test
    fun buildTmdbImageUrl_withPath() {
        val url = buildTmdbImageUrl("/abc123.jpg", "w500")
        assertEquals("https://image.tmdb.org/t/p/w500/abc123.jpg", url)
    }

    @Test
    fun buildTmdbImageUrl_withNullPath() {
        val url = buildTmdbImageUrl(null, "w500")
        assertNull(url)
    }

    @Test
    fun buildTmdbImageUrl_withFullUrl() {
        val url = buildTmdbImageUrl("https://example.com/image.jpg", "w500")
        assertEquals("https://example.com/image.jpg", url)
    }

    @Test
    fun normalizeLanguageCode_normalizesCorrectly() {
        assertEquals("ES", normalizeLanguageCode("es"))
        assertEquals("EN", normalizeLanguageCode("en"))
        assertEquals("LATAM", normalizeLanguageCode("lat"))
        assertEquals("LATAM", normalizeLanguageCode("latino"))
        assertEquals("ES", normalizeLanguageCode(""))
        assertEquals("ES", normalizeLanguageCode(null))
    }

    @Test
    fun languageDisplayLabel_returnsCorrectLabels() {
        assertEquals("Inglés", languageDisplayLabel("EN"))
        assertEquals("Español Latinoamericano", languageDisplayLabel("LATAM"))
        assertEquals("Español", languageDisplayLabel("ES"))
    }

    @Test
    fun contentKind_toApiType() {
        assertEquals("channels", ContentKind.CHANNEL.toApiType())
        assertEquals("movies", ContentKind.MOVIE.toApiType())
        assertEquals("series", ContentKind.SERIES.toApiType())
    }

    private fun createTestCatalogItem(
        stableId: String = "test_123",
        title: String = "Test Title",
        kind: ContentKind = ContentKind.MOVIE,
        group: String = "ES | Accion",
    ) = CatalogItem(
        stableId = stableId,
        title = title,
        subtitle = "Test Subtitle",
        description = "Test Description",
        imageUrl = "https://example.com/image.jpg",
        kind = kind,
        group = group,
        badgeText = "NEW",
    )
}
