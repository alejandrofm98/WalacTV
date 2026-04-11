package com.example.walactv.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * DataSource que stripea el header PNG falso que StreamWish/FileMoon
 * ponen delante de sus segmentos MPEG-TS.
 *
 * Estructura del segmento tal como llega del CDN:
 *   [89 50 4E 47 0D 0A 1A 0A]  ← Header PNG falso
 *   [FF FF FF ... FF]           ← Padding variable de 0xFF
 *   [47 ...]                    ← Primer sync byte MPEG-TS real (0x47)
 */
@UnstableApi
class StreamWishDataSource(
    private val upstream: HttpDataSource,
) : DataSource {

    companion object {
        private const val TS_SYNC_BYTE = 0x47.toByte()
        private const val TS_PACKET_SIZE = 188
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        private const val MAX_SKIP_BYTES = 8192
    }

    private val prefetchBuffer = ByteArray(MAX_SKIP_BYTES)
    private var prefetchStart = 0
    private var prefetchEnd = 0
    private var headerStripped = false
    private var currentUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        headerStripped = false
        prefetchStart = 0
        prefetchEnd = 0
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (headerStripped) {
            return upstream.read(buffer, offset, length)
        }
        stripFakeHeader()

        // Si quedan bytes en el prefetchBuffer, devolverlos primero
        if (prefetchStart < prefetchEnd) {
            val available = prefetchEnd - prefetchStart
            val toCopy = minOf(available, length)
            System.arraycopy(prefetchBuffer, prefetchStart, buffer, offset, toCopy)
            prefetchStart += toCopy
            return toCopy
        }

        return upstream.read(buffer, offset, length)
    }

    private fun stripFakeHeader() {
        var totalRead = 0
        while (totalRead < MAX_SKIP_BYTES) {
            val bytesRead = upstream.read(
                prefetchBuffer,
                totalRead,
                MAX_SKIP_BYTES - totalRead,
            )
            if (bytesRead == C.LENGTH_UNSET) break
            totalRead += bytesRead
            if (totalRead >= TS_PACKET_SIZE * 2) break
        }

        prefetchEnd = totalRead
        headerStripped = true

        if (totalRead == 0) return

        if (!hasFakePngHeader(prefetchBuffer, totalRead)) {
            // No es PNG falso → pasar tal cual desde el inicio
            prefetchStart = 0
            return
        }

        val syncIndex = findValidSyncByte(prefetchBuffer, totalRead)
        prefetchStart = if (syncIndex >= 0) syncIndex else 0
    }

    private fun hasFakePngHeader(data: ByteArray, length: Int): Boolean {
        if (length < PNG_MAGIC.size) return false
        return PNG_MAGIC.indices.all { data[it] == PNG_MAGIC[it] }
    }

    private fun findValidSyncByte(data: ByteArray, length: Int): Int {
        for (i in 0 until length) {
            if (data[i] == TS_SYNC_BYTE) {
                val next = i + TS_PACKET_SIZE
                if (next < length && data[next] == TS_SYNC_BYTE) return i
                if (next >= length) return i
            }
        }
        return -1
    }

    override fun getUri(): Uri? = upstream.uri ?: currentUri

    override fun close() {
        upstream.close()
        prefetchStart = 0
        prefetchEnd = 0
        headerStripped = false
    }
}