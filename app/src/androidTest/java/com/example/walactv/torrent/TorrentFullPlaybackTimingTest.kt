package com.example.walactv.torrent

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.walactv.data.remote.torrent.TorrentioClient
import com.example.walactv.datasource.torrent.TorrentEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Cronometro end-to-end: Torrentio -> motor libtorrent -> HTTP local ->
 * ExoPlayer hasta el PRIMER FOTOGRAMA. Mide un torrent con muchas semillas
 * y otro con pocas para comparar.
 */
@RunWith(AndroidJUnit4::class)
class TorrentFullPlaybackTimingTest {

    private val tag = "TORRENT_TIMING"

    @Test
    fun measureFullPlaybackStartup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val streams = TorrentioClient.episodeStreams("tt14688458", 3, 3)
            assertTrue("Sin streams de Torrentio (¿red?)", streams.isNotEmpty())
            val torrents = streams.filter { it.isTorrent && !it.infoHash.isNullOrBlank() }

            // El mismo torrent 4K que reproduce Nuvio en la comparativa
            // (Silo S03E03 ATVP 2160p, 317 seeds). Si no esta en nuestra
            // lista, se usa el de mas semillas.
            val sameAsNuvio = torrents.firstOrNull {
                it.infoHash.equals("5587c83a537416024e2ed3cca0111a033e105419", ignoreCase = true)
            }
            val highSeed = sameAsNuvio ?: torrents.maxByOrNull { it.seeders ?: 0 }!!
            val lowSeed = torrents.filter { (it.seeders ?: 0) in 3..15 }
                .maxByOrNull { it.sizeBytes ?: 0L }
                ?: torrents.minByOrNull { it.seeders ?: Int.MAX_VALUE }!!

            Log.i(tag, "HIGH: seeds=${highSeed.seeders} size=${highSeed.sizeBytes} title=${highSeed.torrentTitle?.take(70)}")
            Log.i(tag, "LOW: seeds=${lowSeed.seeders} size=${lowSeed.sizeBytes} title=${lowSeed.torrentTitle?.take(70)}")

            // Un solo engine (una sola sesion libtorrent), como en la app.
            // Warmup + asentamiento: igual que la app al arrancar, para medir
            // el caso realista (el usuario navega antes de reproducir).
            val engine = TorrentEngine(context)
            engine.warmup()
            Log.i(tag, "warmup lanzado, esperando 30s de bootstrap DHT…")
            kotlinx.coroutines.delay(30_000)
            try {
                measureFirstFrame(context, engine, highSeed.infoHash!!, highSeed.fileIdx, "HIGH")
                measureFirstFrame(context, engine, lowSeed.infoHash!!, lowSeed.fileIdx, "LOW")
            } finally {
                engine.stopStream(clearFiles = true)
            }
        }
    }

    private suspend fun measureFirstFrame(
        context: android.content.Context,
        engine: TorrentEngine,
        infoHash: String,
        fileIdx: Int?,
        label: String,
    ) {
        val readyLatch = CountDownLatch(1)
        val engineListener = object : TorrentEngine.Listener {
            override fun onMetadataReady(fileCount: Int) {}
            override fun onReady(engine: TorrentEngine) {
                readyLatch.countDown()
            }

            override fun onError(message: String) {
                Log.w(tag, "$label engine error: $message")
            }

            override fun onProgress(percent: Float) {}
        }
        engine.addListener(engineListener)
        var player: ExoPlayer? = null
        var surface: Surface? = null
        var surfaceTexture: SurfaceTexture? = null
        try {
            val t0 = System.currentTimeMillis()
            // Flujo de la app: el player espera la URL de TorrServer
            // (demonio + magnet + fichero resuelto) antes de preparar.
            val url = engine.startTorrentAndGetUrl(infoHash, fileIdx)
            val tUrl = System.currentTimeMillis() - t0
            Log.i(tag, "$label URL TorrServer lista en ${tUrl}ms: ${url?.takeLast(60)}")
            checkNotNull(url) { "$label: TorrServer no resolvio URL" }

            val firstFrameLatch = CountDownLatch(1)
            val tracksLatch = CountDownLatch(1)
            val errorRef = AtomicReference<String?>(null)
            val t0Tracks = System.currentTimeMillis()
            val main = Handler(Looper.getMainLooper())
            val buildLatch = CountDownLatch(1)
            main.post {
                try {
                    surfaceTexture = SurfaceTexture(0)
                    surface = Surface(surfaceTexture)
                    val loadControl = DefaultLoadControl.Builder()
                        .setBufferDurationsMs(8_000, 60_000, 1_200, 2_500)
                        .build()
                    // Mismos timeouts que la app (120s lectura): el servidor
                    // local bloquea hasta que llega la pieza.
                    val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setConnectTimeoutMs(15_000)
                        .setReadTimeoutMs(120_000)
                    val mediaSourceFactory =
                        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                            .setDataSourceFactory(dataSourceFactory)
                    player = ExoPlayer.Builder(context)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .setLoadControl(loadControl)
                        .build()
                        .also { exo ->
                            exo.setVideoSurface(surface)
                            exo.addListener(object : Player.Listener {
                                override fun onRenderedFirstFrame() {
                                    firstFrameLatch.countDown()
                                }

                                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                                    val hasVideo = (0 until tracks.groups.size).any { gi ->
                                        (0 until tracks.groups[gi].length).any { ti ->
                                            tracks.groups[gi].getTrackFormat(ti).sampleMimeType
                                                ?.startsWith("video/") == true
                                        }
                                    }
                                    if (hasVideo && tracksLatch.count > 0) {
                                        Log.i(
                                            tag,
                                            "$label FORMATO VIDEO extraido en " +
                                                "${System.currentTimeMillis() - t0Tracks}ms",
                                        )
                                        tracksLatch.countDown()
                                    }
                                }

                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                    errorRef.set("${error.errorCode} ${error.message}")
                                    firstFrameLatch.countDown()
                                }
                            })
                            exo.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
                            exo.prepare()
                            exo.playWhenReady = true
                        }
                } finally {
                    buildLatch.countDown()
                }
            }
            buildLatch.await(15, TimeUnit.SECONDS)
            // Muestreo cada 5s: peers, seeds, tasa y MB para ver donde espera.
            val sampler = Thread {
                val t0s = System.currentTimeMillis()
                while (firstFrameLatch.count > 0 &&
                    System.currentTimeMillis() - t0s < 240_000
                ) {
                    val st = engine.stats.value
                    Log.i(
                        tag,
                        "$label SAMPLE t+${(System.currentTimeMillis() - t0) / 1000}s " +
                            "peers=${st?.peers} seeds=${st?.seeds} " +
                            "rate=${(st?.rateBytesPerSec ?: 0) / 1024}KB/s " +
                            "mb=${(st?.downloadedBytes ?: 0) / (1024 * 1024)}",
                    )
                    try {
                        Thread.sleep(5_000)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
            sampler.isDaemon = true
            sampler.start()
            val firstFrameOk = firstFrameLatch.await(240, TimeUnit.SECONDS)
            val tFrame = System.currentTimeMillis() - t0
            val downloaded = engine.stats.value?.downloadedBytes ?: -1
            Log.i(
                tag,
                "$label PRIMER FOTOGRAMA en ${tFrame}ms (~${tFrame / 1000}s) " +
                    "ok=$firstFrameOk error=${errorRef.get()} descargados=${downloaded / 1024}KB",
            )
        } finally {
            val p = player
            Handler(Looper.getMainLooper()).post {
                runCatching { p?.release() }
                runCatching { surface?.release() }
                runCatching { surfaceTexture?.release() }
            }
            engine.removeListener(engineListener)
            engine.stopStream(clearFiles = false)
        }
    }
}
