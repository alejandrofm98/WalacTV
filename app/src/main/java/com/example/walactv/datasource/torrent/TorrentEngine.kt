package com.example.walactv.datasource.torrent

import android.content.Context
import android.util.Log
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.MetadataReceivedAlert
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * Motor BitTorrent embebido (libtorrent4j) que descarga un magnet de Torrentio
 * de forma secuencial y expone el archivo de video seleccionado para que
 * [TorrentDataSource] lo lea directamente del disco (las piezas descargadas
 * por libtorrent se materializan en [saveDirectory]).
 *
 * Flujo:
 *  1. [startStream] recibe un infoHash (y fileIdx opcional) y anade el magnet
 *     a la sesion con descarga secuencial.
 *  2. Cuando llegan los metadatos ([MetadataReceivedAlert]) se selecciona el
 *     archivo de video (el mayor, o el indicado por fileIdx), se priorizan sus
 *     piezas y se notifica via [Listener.onReady].
 *  3. [readRange] lee bytes del archivo con RandomAccessFile, esperando a que
 *     las piezas implicadas esten descargadas (havePiece + polling).
 */
@Singleton
class TorrentEngine @Inject constructor(
    context: Context,
) {
    private val appContext: Context = context.applicationContext

    companion object {
        private const val TAG = "TorrentEngine"
        private const val MIN_PREPARE_PIECES = 4
        private const val MAX_PREPARE_PIECES = 32
        private const val PIECE_WAIT_TIMEOUT_MS = 20_000L
        private const val PIECE_POLL_MS = 250L
    }

    interface Listener {
        fun onMetadataReady(fileCount: Int)
        fun onReady(engine: TorrentEngine)
        fun onError(message: String)
        fun onProgress(percent: Float)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile private var session: SessionManager? = null
    @Volatile private var handle: TorrentHandle? = null
    @Volatile private var info: TorrentInfo? = null
    @Volatile private var selectedFileIndex = -1
    @Volatile private var selectedFileSize = 0L
    @Volatile private var activeInfoHash: String? = null
    @Volatile var isReady = false
        private set

    private val metadataLatch = CountDownLatch(1)

    private val alertListener = object : AlertListener {
        override fun types(): IntArray = intArrayOf(
            AlertType.METADATA_RECEIVED.swig(),
            AlertType.METADATA_FAILED.swig(),
        )

        override fun alert(a: Alert<*>?) {
            when (a) {
                is MetadataReceivedAlert -> onMetadataReceived()
                else -> Unit
            }
        }
    }

    fun addListener(listener: Listener) = listeners.add(listener)
    fun removeListener(listener: Listener) = listeners.remove(listener)

    /** Progreso global de descarga del stream actual (0..1). */
    fun downloadProgress(): Float = runCatching { handle?.status()?.progress() ?: 0f }.getOrDefault(0f)

    fun isStreaming(): Boolean = activeInfoHash != null

    fun currentInfoHash(): String? = activeInfoHash

    /** Directorio donde libtorrent materializa los archivos. */
    private fun saveDirectory(): File =
        File(appContext.cacheDir, "torrent").apply { mkdirs() }

    /**
     * Arranca la descarga de un magnet. Devuelve inmediatamente; el callback
     * [Listener.onReady] se invoca cuando el archivo de video esta listo.
     */
    fun startStream(infoHash: String, fileIdx: Int? = null) {
        stopStream()
        val hash = infoHash.trim().lowercase()
        require(hash.length == 40 && hash.all { it.isDigit() || it in 'a'..'f' }) {
            "infoHash torrent invalido"
        }
        Log.d(TAG, "startStream: $hash fileIdx=$fileIdx")

        activeInfoHash = hash
        isReady = false
        selectedFileIndex = -1
        selectedFileSize = 0L
        if (fileIdx != null) selectedFileIndex = fileIdx

        val mgr = session ?: createSession()
        mgr.addListener(alertListener)

        val magnet = "magnet:?xt=urn:btih:$hash"
        thread(name = "walac-torrent-start") {
            try {
                mgr.download(magnet, saveDirectory(), TorrentFlags.SEQUENTIAL_DOWNLOAD)
                val waitOk = metadataLatch.await(30, TimeUnit.SECONDS)
                if (!waitOk) {
                    Log.e(TAG, "startStream: timeout esperando metadatos")
                    listeners.forEach { it.onError("No se pudieron obtener los metadatos del torrent (timeout)") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startStream: error", e)
                listeners.forEach { it.onError("No se pudo iniciar el torrent: ${e.message}") }
            }
        }
    }

    private fun createSession(): SessionManager {
        Log.d(TAG, "createSession: iniciando sesion libtorrent")
        val mgr = SessionManager()
        mgr.start(SessionParams())
        session = mgr
        return mgr
    }

    private fun onMetadataReceived() {
        val mgr = session ?: return
        val hash = activeInfoHash ?: return
        val found = try {
            mgr.find(org.libtorrent4j.Sha1Hash.parseHex(hash))
        } catch (e: Exception) {
            Log.e(TAG, "onMetadataReceived: find fallo", e)
            null
        }
        if (found == null || !found.isValid()) {
            Log.e(TAG, "onMetadataReceived: handle invalido para $hash")
            listeners.forEach { it.onError("No se encontro el torrent tras recibir metadatos") }
            return
        }
        handle = found
        val ti = found.torrentFile()
        info = ti
        Log.d(TAG, "onMetadataReceived: ${ti.name()} ${ti.numFiles()} archivos, ${ti.numPieces()} piezas, ${ti.pieceLength()}B/pieza")

        // Seleccionar archivo: el indicado por fileIdx o el mayor de video
        val fs = ti.files()
        val fileIdx = if (selectedFileIndex >= 0 && selectedFileIndex < fs.numFiles()) {
            selectedFileIndex
        } else {
            largestVideoFileIndex(ti)
        }
        if (fileIdx < 0) {
            listeners.forEach { it.onError("El torrent no contiene archivos de video reproducibles") }
            return
        }
        selectedFileIndex = fileIdx
        selectedFileSize = fs.fileSize(fileIdx)
        Log.d(TAG, "onMetadataReceived: archivo seleccionado #$fileIdx '${fs.fileName(fileIdx)}' size=$selectedFileSize")

        // Priorizar solo el archivo elegido
        for (i in 0 until fs.numFiles()) {
            found.filePriority(i, if (i == fileIdx) Priority.TOP_PRIORITY else Priority.IGNORE)
        }
        // Prioridad alta a las primeras piezas para arrancar rapido
        val pieceLength = ti.pieceLength()
        val preparePieces = (selectedFileSize / pieceLength).toInt().coerceIn(MIN_PREPARE_PIECES, MAX_PREPARE_PIECES)
        for (i in 0 until preparePieces.coerceAtMost(ti.numPieces())) {
            found.setPieceDeadline(i, 2_000)
            found.piecePriority(i, Priority.TOP_PRIORITY)
        }

        listeners.forEach { it.onMetadataReady(fs.numFiles()) }
        isReady = true
        listeners.forEach { it.onReady(this) }
    }

    private fun largestVideoFileIndex(ti: TorrentInfo): Int {
        val fs = ti.files()
        val videoExtensions = setOf("mp4", "mkv", "avi", "m4v", "mov", "webm", "ts", "mpg", "mpeg")
        var bestIdx = -1
        var bestSize = -1L
        for (i in 0 until fs.numFiles()) {
            val size = fs.fileSize(i)
            if (size < bestSize) continue
            val ext = fs.fileName(i).substringAfterLast('.', "").lowercase()
            if (ext in videoExtensions && size > bestSize) {
                bestIdx = i
                bestSize = size
            }
        }
        // Fallback: el archivo mas grande aunque no sea extension conocida
        if (bestIdx < 0) {
            for (i in 0 until fs.numFiles()) {
                val size = fs.fileSize(i)
                if (size > bestSize) {
                    bestIdx = i
                    bestSize = size
                }
            }
        }
        return bestIdx
    }

    /**
     * Espera (con polling) a que la pieza [pieceIndex] este descargada.
     * Devuelve true cuando esta disponible o false si timeout.
     */
    private fun awaitPieceAvailable(pieceIndex: Int): Boolean {
        val h = handle ?: return false
        val deadline = System.currentTimeMillis() + PIECE_WAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!h.isValid()) return false
            try {
                if (h.havePiece(pieceIndex)) return true
            } catch (_: Exception) {
                return false
            }
            try {
                Thread.sleep(PIECE_POLL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return false
    }

    /**
     * Ruta absoluta del archivo seleccionado dentro del savePath de libtorrent.
     */
    private fun selectedFilePath(): String? {
        val h = handle ?: return null
        val ti = info ?: return null
        val idx = selectedFileIndex
        if (idx < 0 || idx >= ti.files().numFiles()) return null
        return File(h.savePath(), ti.files().filePath(idx)).absolutePath
    }

    /**
     * Lee [length] bytes a partir de [offset] del archivo de video, esperando
     * a que las piezas implicadas esten descargadas. Devuelve null si timeout.
     */
    fun readRange(offset: Long, length: Int): ByteArray? {
        if (!isReady) return null
        val path = selectedFilePath() ?: return null
        val pieceLength = (info?.pieceLength() ?: 0).toLong()
        if (pieceLength <= 0) return null

        // Esperar a que la primera pieza del rango este disponible
        val firstPiece = (offset / pieceLength).toInt()
        if (!awaitPieceAvailable(firstPiece)) {
            Log.w(TAG, "readRange: timeout esperando pieza $firstPiece en offset $offset")
            return null
        }

        return try {
            RandomAccessFile(File(path), "r").use { raf ->
                raf.seek(offset)
                val buffer = ByteArray(length)
                var read = 0
                while (read < length) {
                    // Si cruzamos a una pieza nueva, esperar a que este lista
                    val currentPiece = ((offset + read) / pieceLength).toInt()
                    if (currentPiece != firstPiece && !awaitPieceAvailable(currentPiece)) {
                        Log.w(TAG, "readRange: timeout esperando pieza $currentPiece")
                        break
                    }
                    val n = raf.read(buffer, read, length - read)
                    if (n <= 0) break
                    read += n
                }
                if (read == 0) null else buffer.copyOf(read)
            }
        } catch (e: Exception) {
            Log.e(TAG, "readRange: error leyendo $path en $offset", e)
            null
        }
    }

    fun selectedFileSize(): Long = selectedFileSize

    fun selectedFileName(): String? {
        val ti = info ?: return null
        val idx = selectedFileIndex
        if (idx < 0 || idx >= ti.files().numFiles()) return null
        return ti.files().fileName(idx)
    }

    fun stopStream() {
        Log.d(TAG, "stopStream")
        val h = handle
        val mgr = session
        if (h != null && h.isValid()) {
            runCatching { mgr?.remove(h) }
        }
        handle = null
        info = null
        selectedFileIndex = -1
        selectedFileSize = 0L
        isReady = false
        activeInfoHash = null
        metadataLatch.countDown() // liberar cualquier await pendiente
        // Limpiar archivos temporales
        runCatching { saveDirectory().deleteRecursively() }
    }

    fun destroy() {
        stopStream()
        session?.removeListener(alertListener)
        runCatching { session?.swig()?.abort() }
        session = null
    }
}
