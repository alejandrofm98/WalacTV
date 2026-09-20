package com.example.walactv.datasource.torrent

import android.content.Context
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Foto de estado del torrent activo para la pantalla de carga del player. */
data class TorrentStats(
    val metadataReady: Boolean,
    val peers: Int,
    val seeds: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val rateBytesPerSec: Long,
    val etaSeconds: Int?,
    val progressPercent: Int,
    /** Texto de fase puntual (p.ej. descarga del binario). Si existe, manda sobre los textos genericos. */
    val statusMessage: String? = null,
)

/**
 * Motor BitTorrent via TorrServer local (mismo modelo que Stremio/Nuvio).
 *
 * Mantiene la API publica del motor anterior (libtorrent embebido) para no
 * tocar a sus consumidores: el demonio TorrServer corre como proceso propio
 * con sesion DHT persistente y sirve cada fichero por HTTP con rangos, asi
 * que ExoPlayer prepara y busca sobre la URL sin esperas de piezas.
 *
 * Flujo:
 *  1. [startStream] recibe un infoHash (y fileIdx opcional), asegura el
 *     demonio, añade el magnet y resuelve el fichero (por id, posicion o
 *     mayor video). Notifica via [Listener.onReady].
 *  2. [startTorrentAndGetUrl] hace lo mismo de forma suspendida y devuelve
 *     la URL HTTP lista para el player.
 *  3. [stats] publica peers/seeds/velocidad/MB para la pantalla de carga.
 */
@Singleton
class TorrentEngine @Inject constructor(
    context: Context,
) {
    private val appContext: Context = context.applicationContext

    companion object {
        private const val TAG = "TorrentEngine"
        private const val METADATA_WAIT_TIMEOUT_MS = 15_000L
        private const val STATS_INTERVAL_MS = 1_000L

        /** Trackers publicos para descubrimiento rapido de peers (ademas de DHT). */
        private val TORRENT_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.tiny-vps.com:6969/announce",
        )

        private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "m4v", "mov", "webm", "ts", "mpg", "mpeg")
    }

    interface Listener {
        fun onMetadataReady(fileCount: Int)
        fun onReady(engine: TorrentEngine)
        fun onError(message: String)
        fun onProgress(percent: Float)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    private val binary = TorrServerBinary(appContext)
    private val api = TorrServerApi(binary.baseUrl)

    @Volatile private var activeInfoHash: String? = null
    @Volatile private var activeMagnet: String? = null
    @Volatile private var activeFileId: Int = -1
    @Volatile private var activeFileName: String? = null
    @Volatile private var activeFileSize: Long = 0L
    @Volatile private var activeUrl: String? = null

    @Volatile var isReady = false
        private set

    private val _stats = MutableStateFlow<TorrentStats?>(null)
    val stats: StateFlow<TorrentStats?> = _stats

    private val statsRunning = AtomicBoolean(false)
    private val streamGeneration = AtomicLong(0)

    fun addListener(listener: Listener) = listeners.add(listener)
    fun removeListener(listener: Listener) = listeners.remove(listener)

    /** Progreso global de descarga del stream actual (0..1). */
    fun downloadProgress(): Float {
        val size = activeFileSize
        if (size <= 0) return 0f
        val done = _stats.value?.downloadedBytes ?: 0L
        return (done.toFloat() / size).coerceIn(0f, 1f)
    }

    /** Diagnostico del torrent activo: peers, seeds y velocidad. */
    fun debugStatus(): String {
        val st = _stats.value
        return "torrserver peers=${st?.peers ?: 0} seeds=${st?.seeds ?: 0} " +
            "rate=${(st?.rateBytesPerSec ?: 0) / 1024}KB/s " +
            "file='${activeFileName.orEmpty()}' size=${activeFileSize / (1024 * 1024)}MB"
    }

    fun isStreaming(): Boolean = activeInfoHash != null

    fun currentInfoHash(): String? = activeInfoHash

    fun selectedFileSize(): Long = activeFileSize

    fun selectedFileName(): String? = activeFileName

    fun currentStreamGeneration(): Long = streamGeneration.get()

    /** TorrServer busca por rangos de forma nativa: sin-op para compatibilidad. */
    fun prioritizePosition(@Suppress("UNUSED_PARAMETER") fraction: Float) {
    }

    /** TorrServer gestiona su propia cache: sin-op para compatibilidad. */
    fun maybeRecycle(@Suppress("UNUSED_PARAMETER") fraction: Float) {
    }

    /**
     * Precalienta el demonio TorrServer al arrancar la app: la primera
     * reproduccion no paga ni la descarga del binario ni el arranque del
     * proceso. El demonio queda vivo (sesion DHT caliente) entre
     * reproducciones e incluso reinicios de la app.
     */
    fun warmup() {
        thread(name = "walac-torrserver-warmup", isDaemon = true) {
            runBlocking {
                runCatching { binary.ensureRunning() }
                    .onFailure { Log.w(TAG, "warmup: ${it.message}") }
                    .onSuccess { Log.d(TAG, "warmup: TorrServer listo") }
            }
        }
    }

    /**
     * Arranca la descarga de un magnet. Devuelve inmediatamente; el callback
     * [Listener.onReady] se invoca cuando la URL reproducible esta lista.
     * [fileIdx] es el indice 0-based del fichero (igual que Torrentio).
     * [resumeFraction] se ignora: el servidor busca por rangos de forma nativa.
     */
    fun startStream(infoHash: String, fileIdx: Int? = null, resumeFraction: Float? = null) {
        val hash = infoHash.trim().lowercase()
        require(hash.length == 40 && hash.all { it.isDigit() || it in 'a'..'f' }) {
            "infoHash torrent invalido"
        }
        // Idempotencia: mismo hash ya resuelto, reutilizar sin reiniciar.
        if (hash == activeInfoHash && activeUrl != null && isReady) {
            Log.d(TAG, "startStream: mismo hash ya activo, reutilizando")
            _stats.value = _stats.value?.copy(metadataReady = true)
            listeners.forEach { it.onReady(this) }
            return
        }
        stopStream(clearFiles = false)
        Log.d(TAG, "startStream: $hash fileIdx=$fileIdx")
        activeInfoHash = hash
        isReady = false
        streamGeneration.incrementAndGet()
        _stats.value = TorrentStats(
            metadataReady = false, peers = 0, seeds = 0,
            downloadedBytes = 0, totalBytes = 0, rateBytesPerSec = 0,
            etaSeconds = null, progressPercent = 0,
        )
        thread(name = "walac-torrent-start", isDaemon = true) {
            runBlocking {
                val url = runCatching { startTorrentAndGetUrl(hash, fileIdx) }.getOrNull()
                if (url != null) {
                    listeners.forEach { it.onReady(this@TorrentEngine) }
                } else if (activeInfoHash == hash) {
                    listeners.forEach { it.onError("No se pudo iniciar el torrent en TorrServer") }
                }
            }
        }
    }

    /**
     * Asegura demonio + torrent y devuelve la URL HTTP reproducible, o null
     * si falla. Es la via que usa el player (necesita la URL antes de
     * preparar ExoPlayer).
     */
    suspend fun startTorrentAndGetUrl(infoHash: String, fileIdx: Int?): String? = withContext(Dispatchers.IO) {
        val hash = infoHash.trim().lowercase()
        val previous = activeInfoHash
        if (previous != null && previous != hash) {
            thread(name = "walac-torrent-drop", isDaemon = true) {
                runBlocking { runCatching { api.dropTorrent(previous) } }
            }
        }
        activeInfoHash = hash
        isReady = false
        val generation = streamGeneration.incrementAndGet()

        try {
            binary.ensureRunning()
            if (activeInfoHash != hash) return@withContext null

            val magnet = buildMagnet(hash)
            activeMagnet = magnet
            val serverHash = api.addTorrent(magnet) ?: run {
                Log.e(TAG, "TorrServer no acepto el magnet ${hash.take(8)}")
                return@withContext null
            }
            Log.d(TAG, "torrent añadido: ${serverHash.take(8)} fileIdx=$fileIdx")

            val resolved = resolveFile(serverHash, fileIdx) ?: run {
                Log.e(TAG, "sin ficheros reproducibles para ${hash.take(8)}")
                return@withContext null
            }
            if (activeInfoHash != hash || streamGeneration.get() != generation) return@withContext null

            activeFileId = resolved.id
            activeFileName = resolved.path.substringAfterLast('/').ifBlank { resolved.path }
            activeFileSize = resolved.length
            val url = api.streamUrl(magnet, resolved.id)
            activeUrl = url
            isReady = true
            listeners.forEach { it.onMetadataReady(-1) }
            startStatsLoop(serverHash, generation)
            Log.i(TAG, "stream listo: '${activeFileName}' ${activeFileSize / (1024 * 1024)}MB")
            url
        } catch (e: Exception) {
            Log.e(TAG, "startTorrentAndGetUrl: ${e.message}")
            if (activeInfoHash == hash) {
                isReady = false
                _stats.value = _stats.value?.copy(statusMessage = null)
            }
            null
        }
    }

    /**
     * Resuelve el fichero a reproducir: primero id directo (fileIdx+1, los
     * ids del servidor suelen ser 1-based), luego posicion, luego el mayor
     * video. Espera a los metadatos hasta el timeout.
     */
    private suspend fun resolveFile(serverHash: String, fileIdx: Int?): TorrServerFile? {
        val deadline = System.currentTimeMillis() + METADATA_WAIT_TIMEOUT_MS
        var files: List<TorrServerFile> = emptyList()
        while (System.currentTimeMillis() < deadline) {
            files = api.getTorrentStats(serverHash)?.files.orEmpty()
            if (files.isNotEmpty()) break
            Log.d(TAG, "esperando metadatos del torrent…")
            kotlinx.coroutines.delay(1_000)
        }
        if (files.isEmpty()) {
            Log.e(TAG, "sin metadatos tras ${METADATA_WAIT_TIMEOUT_MS / 1000}s")
            return null
        }
        Log.d(TAG, "torrent con ${files.size} ficheros")
        if (fileIdx != null) {
            files.firstOrNull { it.id == fileIdx + 1 }?.let {
                Log.d(TAG, "fichero por id: ${it.path} (${it.length / (1024 * 1024)}MB)")
                return it
            }
            files.getOrNull(fileIdx)?.let {
                Log.d(TAG, "fichero por posicion: ${it.path} (${it.length / (1024 * 1024)}MB)")
                return it
            }
        }
        val video = files
            .filter { it.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS }
            .maxByOrNull { it.length }
            ?: files.maxByOrNull { it.length }
        Log.d(TAG, "fichero por mayor video: ${video?.path} (${(video?.length ?: 0) / (1024 * 1024)}MB)")
        return video
    }

    fun stopStream(clearFiles: Boolean = true) {
        val hash = activeInfoHash
        activeInfoHash = null
        activeMagnet = null
        activeUrl = null
        activeFileId = -1
        activeFileName = null
        activeFileSize = 0L
        isReady = false
        statsRunning.set(false)
        if (hash != null) {
            thread(name = "walac-torrent-stop", isDaemon = true) {
                runBlocking {
                    runCatching { api.dropTorrent(hash) }
                    Log.d(TAG, "torrent soltado: ${hash.take(8)} (clearFiles=$clearFiles)")
                }
            }
        }
    }

    fun stopStreamIfOwner(ownerGeneration: Long, clearFiles: Boolean = true) {
        if (streamGeneration.get() == ownerGeneration) stopStream(clearFiles)
    }

    private fun buildMagnet(hash: String): String {
        return buildString {
            append("magnet:?xt=urn:btih:$hash")
            for (tr in TORRENT_TRACKERS) {
                append("&tr=").append(java.net.URLEncoder.encode(tr, "UTF-8"))
            }
        }
    }

    private fun startStatsLoop(serverHash: String, generation: Long) {
        if (!statsRunning.compareAndSet(false, true)) return
        thread(name = "walac-torrent-stats", isDaemon = true) {
            var lastLogMs = 0L
            while (statsRunning.get() && streamGeneration.get() == generation) {
                val st = runCatching { runBlocking { api.getTorrentStats(serverHash) } }.getOrNull()
                val hash = activeInfoHash
                if (st != null && hash != null) {
                    val size = activeFileSize
                    val done = st.loadedSize.coerceAtMost(size.takeIf { it > 0 } ?: st.loadedSize)
                    val remaining = (size - done).coerceAtLeast(0)
                    val eta = if (size > 0 && remaining > 0 && st.downloadSpeed > 0) {
                        (remaining / st.downloadSpeed).toInt()
                    } else {
                        null
                    }
                    val pct = if (size > 0) (done * 100 / size).toInt().coerceIn(0, 100) else 0
                    _stats.value = TorrentStats(
                        metadataReady = isReady,
                        peers = st.peers,
                        seeds = st.seeds,
                        downloadedBytes = done,
                        totalBytes = size,
                        rateBytesPerSec = st.downloadSpeed,
                        etaSeconds = eta,
                        progressPercent = pct,
                    )
                    val now = System.currentTimeMillis()
                    if (now - lastLogMs > 10_000) {
                        lastLogMs = now
                        Log.d(TAG, "stats: $pct% ${done / (1024 * 1024)}MB " +
                            "rate=${st.downloadSpeed / 1024}KB/s seeds=${st.seeds} peers=${st.peers} eta=${eta ?: "-"}")
                    }
                }
                try {
                    Thread.sleep(STATS_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
    }
}
