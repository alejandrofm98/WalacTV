package com.example.walactv.local

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentDatabaseSmokeTest {
    private lateinit var database: ContentDatabase

    @Before
    fun setUp() {
        database = ContentDatabase.getDatabase(ApplicationProvider.getApplicationContext())
        database.clearAllTables()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun databaseCreatesCurrentSchemaAndPersistsCatalogRows() = runBlocking {
        val channel = ChannelEntity(
            id = "channel-1",
            numero = 1,
            providerId = "provider-1",
            logo = "",
            countries = "ES",
            nombreNormalizado = "Canal Uno",
            grupoNormalizado = "Noticias",
        )
        database.channelDao().replaceAll(listOf(channel))

        val loaded = database.channelDao().getAllPaged(10, 0)

        assertEquals(listOf(channel), loaded)
        assertTrue(loaded.first().toCatalogItem("user", "pass").title.isNotEmpty())
    }
}
