package com.example.walactv.torrent

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.walactv.data.remote.torrent.TorrentioClient
import com.example.walactv.datasource.torrent.TorrentEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Mide el tiempo real de arranque de un torrent con muchas semillas en este
 * dispositivo/emulador: metadatos DHT + primeros 5 MB de datos.
 * No es un test de regression estricto, es un cronometro instrumentado.
 */
@RunWith(AndroidJUnit4::class)
class TorrentStartupTimingTest {

    private val tag = "TORRENT_TIMING"

    @Test
    fun measureHighSeedTorrentStartup() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = TorrentEngine(context)

        // 1) Lookup Torrentio (Silo S03E03) y eleccion del torrent con mas seeds.
        val t0Lookup = System.currentTimeMillis()
        val streams = TorrentioClient.episodeStreams("tt14688458", 3, 3)
        val lookupMs = System.currentTimeMillis() - t0Lookup
        Log.i(tag, "torrentio lookup: ${streams.size} streams en ${lookupMs}ms")
        assertTrue("Sin streams de Torrentio (¿red?)", streams.isNotEmpty())

        val best = streams.filter { it.isTorrent }
            .maxWithOrNull(compareBy({ it.seeders ?: 0 }, { it.sizeBytes ?: 0L }))
        checkNotNull(best) { "Sin torrents" }
        val hash = checkNotNull(best.infoHash)
        Log.i(
            tag,
            "elegido: seeds=${best.seeders} size=${best.sizeBytes} " +
                "fileIdx=${best.fileIdx} hash=${hash.take(8)} " +
                "title=${best.torrentTitle?.take(80)}",
        )

        // 2) Arranque del motor: metadatos + primeros 5 MB.
        val readyLatch = CountDownLatch(1)
        val listener = object : TorrentEngine.Listener {
            override fun onMetadataReady(fileCount: Int) {}
            override fun onReady(engine: TorrentEngine) {
                readyLatch.countDown()
            }

            override fun onError(message: String) {
                Log.w(tag, "engine error: $message")
            }

            override fun onProgress(percent: Float) {}
        }
        engine.addListener(listener)
        try {
            val t0 = System.currentTimeMillis()
            engine.startStream(hash, best.fileIdx)
            val metadataOk = readyLatch.await(120, TimeUnit.SECONDS)
            val tMeta = System.currentTimeMillis() - t0
            Log.i(tag, "METADATOS listos en ${tMeta}ms (ok=$metadataOk)")
            assertTrue("Sin metadatos en 120s", metadataOk)

            val target = 5L * 1024 * 1024
            var downloaded = 0L
            val deadline = System.currentTimeMillis() + 120_000
            while (System.currentTimeMillis() < deadline) {
                downloaded = engine.stats.value?.downloadedBytes ?: 0L
                if (downloaded >= target) break
                kotlinx.coroutines.delay(500)
            }
            val tData = System.currentTimeMillis() - t0
            Log.i(
                tag,
                "PRIMEROS 5MB en ${tData}ms (${downloaded / 1024}KB descargados). " +
                    "TOTAL arranque aprox: ${tData / 1000}s",
            )

            // Segunda ronda con DHT ya caliente: solo metadatos.
            engine.stopStream(clearFiles = true)
            kotlinx.coroutines.delay(1000)
            val readyLatch2 = CountDownLatch(1)
            val listener2 = object : TorrentEngine.Listener {
                override fun onMetadataReady(fileCount: Int) {}
                override fun onReady(engine: TorrentEngine) {
                    readyLatch2.countDown()
                }

                override fun onError(message: String) {}
                override fun onProgress(percent: Float) {}
            }
            engine.addListener(listener2)
            try {
                val t02 = System.currentTimeMillis()
                engine.startStream(hash, best.fileIdx)
                val ok2 = readyLatch2.await(120, TimeUnit.SECONDS)
                Log.i(tag, "RONDA 2 (DHT caliente): metadatos en ${System.currentTimeMillis() - t02}ms (ok=$ok2)")
            } finally {
                engine.removeListener(listener2)
            }
        } finally {
            engine.removeListener(listener)
            engine.stopStream(clearFiles = true)
        }
        }
    }
}
