package com.example.walactv.datasource.torrent

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * DataSource de Media3 que sirve el archivo de video de un torrent a ExoPlayer.
 * En lugar de hacer HTTP, lee los bytes directamente del [TorrentEngine]
 * (que descarga las piezas via libtorrent y las cachea).
 *
 * Soporta seek: [open] recibe el offset en [DataSpec.position] y [read] va
 * avanzando por el archivo, pidiendo las piezas necesarias.
 */
@UnstableApi
class TorrentDataSource(
    private val engine: TorrentEngine,
) : DataSource {

    companion object {
        private const val TAG = "TorrentDataSource"
        private const val CHUNK_SIZE = 64 * 1024

        /** Cuanto espera open() a que los metadatos esten listos antes de fallar. */
        private const val READY_TIMEOUT_MS = 60_000L
        private const val READY_POLL_MS = 250L
    }

    private var currentSpec: DataSpec? = null
    private var position = 0L
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        // Los metadatos del magnet pueden tardar varios segundos. Esperar aqui
        // mantiene a ExoPlayer en BUFFERING; lanzar IOException seria un error
        // fatal de source y cortaria la reproduccion (bug visto en TV).
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        while (!engine.isReady) {
            if (!engine.isStreaming()) {
                throw IOException("El torrent se detuvo antes de estar listo")
            }
            if (System.currentTimeMillis() > deadline) {
                throw IOException("El torrent no esta listo para reproducir (timeout ${READY_TIMEOUT_MS / 1000}s)")
            }
            try {
                Thread.sleep(READY_POLL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Espera del torrent interrumpida")
            }
        }
        Log.d(TAG, "open: position=${dataSpec.position} length=${dataSpec.length} total=${engine.selectedFileSize()} ${engine.debugStatus()}")
        currentSpec = dataSpec
        position = dataSpec.position
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else C.LENGTH_UNSET.toLong()
        return if (dataSpec.length == C.LENGTH_UNSET.toLong()) engine.selectedFileSize() - dataSpec.position else dataSpec.length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val spec = currentSpec ?: return C.RESULT_END_OF_INPUT
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        // Mientras el torrent este activo, esperar piezas NO es un error: un
        // IOException aqui reiniciaria el player en bucle hasta tener el archivo
        // completo (comportamiento observado en TV). Bloquear mantiene a
        // ExoPlayer en BUFFERING y reproduce en cuanto la pieza llegue.
        while (true) {
            val toRead = minOf(length, CHUNK_SIZE, if (bytesRemaining == C.LENGTH_UNSET.toLong()) Int.MAX_VALUE else bytesRemaining.toInt())
            if (toRead <= 0) return C.RESULT_END_OF_INPUT

            val chunk = try {
                engine.readRange(position, toRead)
            } catch (e: Exception) {
                Log.e(TAG, "read: error leyendo range en $position", e)
                throw IOException("Error leyendo del torrent: ${e.message}", e)
            }
            if (chunk != null) {
                System.arraycopy(chunk, 0, buffer, offset, chunk.size)
                position += chunk.size
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                    bytesRemaining -= chunk.size
                }
                return chunk.size
            }
            if (!engine.isStreaming()) {
                throw IOException("Torrent detenido durante la lectura en $position")
            }
            Log.d(TAG, "read: piezas aun no disponibles en $position, esperando…")
        }
    }

    override fun getUri(): Uri? = currentSpec?.uri

    override fun close() {
        currentSpec = null
    }
}

@UnstableApi
class TorrentDataSourceFactory(
    private val engine: TorrentEngine,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = TorrentDataSource(engine)

    companion object {
        fun isTorrentUrl(url: String): Boolean = url.startsWith("magnet:")
    }
}
