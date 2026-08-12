package com.example.walactv

import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.model.playbackContentId
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackContentIdTest {

    @Test
    fun `all catalog types use provider id when available`() {
        listOf(
            ContentKind.CHANNEL,
            ContentKind.EVENT,
            ContentKind.MOVIE,
            ContentKind.SERIES,
            ContentKind.UFC,
        ).forEach { kind ->
            assertEquals("provider-123", item(kind, providerId = "provider-123").playbackContentId())
        }
    }

    @Test
    fun `all catalog types fall back to stable id`() {
        listOf(
            ContentKind.CHANNEL,
            ContentKind.EVENT,
            ContentKind.MOVIE,
            ContentKind.SERIES,
            ContentKind.UFC,
        ).forEach { kind ->
            assertEquals("${kind.name.lowercase()}:123", item(kind).playbackContentId())
        }
    }

    @Test
    fun `discover and global search preserve the same playback id contract`() {
        val discoverItem = item(ContentKind.SERIES, providerId = "episode-123")
        val searchItem = discoverItem.copy()

        assertEquals(discoverItem.playbackContentId(), searchItem.playbackContentId())
    }

    @Test
    fun `continue watching preserves the episode id used by direct playback`() {
        val directItem = item(ContentKind.SERIES, providerId = "episode-123")
        val continueWatchingItem = directItem.copy(stableId = "cw_series:episode-123")

        assertEquals("episode-123", directItem.playbackContentId())
        assertEquals("episode-123", continueWatchingItem.playbackContentId())
    }

    private fun item(kind: ContentKind, providerId: String? = null) = CatalogItem(
        stableId = "${kind.name.lowercase()}:123",
        providerId = providerId,
        title = "Test item",
        subtitle = "",
        description = "",
        imageUrl = "",
        kind = kind,
        group = "",
        badgeText = "",
    )
}
