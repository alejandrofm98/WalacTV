package com.example.walactv.datasource

import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource

@UnstableApi
class StreamWishDataSourceFactory(
    private val userAgent: String = "WalacTV/AndroidTV",
    private val referer: String? = null,
    private val origin: String? = null,
    private val connectTimeoutMs: Int = 30_000,
    private val readTimeoutMs: Int = 30_000,
    private val extraHeaders: Map<String, String> = emptyMap(),
) : DataSource.Factory {

    companion object {
        private const val TAG = "StreamWishDSFactory"

        private val STREAMWISH_DOMAINS = listOf(
            "streamwish", "filemoon", "hglamioz", "wishembed",
            "swdyu", "sfastwish", "flaswish", "strwish",
        )

        fun isStreamWishUrl(url: String): Boolean {
            val lower = url.lowercase()
            return STREAMWISH_DOMAINS.any { lower.contains(it) }
        }
    }

    private fun buildHttpDataSource(): DefaultHttpDataSource {
        val headers = buildMap {
            put("Accept", "*/*")
            put("Accept-Encoding", "gzip, deflate")
            put("Connection", "keep-alive")
            if (referer != null) put("Referer", referer)
            if (origin != null) put("Origin", origin)
            putAll(extraHeaders)
        }

        Log.d(TAG, "buildHttpDataSource: referer=$referer, origin=$origin, userAgent=${userAgent.take(50)}")

        return DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setConnectTimeoutMs(connectTimeoutMs)
            .setReadTimeoutMs(readTimeoutMs)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
            .createDataSource()
    }

    override fun createDataSource(): DataSource {
        val http = buildHttpDataSource()
        return StreamWishDataSource(http)
    }
}