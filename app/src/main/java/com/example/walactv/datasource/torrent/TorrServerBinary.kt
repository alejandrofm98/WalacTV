package com.example.walactv.datasource.torrent

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Gestiona el binario de TorrServer (servidor BitTorrent local, mismo modelo
 * que usan Stremio/Nuvio): vive como proceso propio con sesion DHT
 * persistente y sirve los torrents por HTTP.
 *
 * El binario viaja empaquetado en jniLibs/<abi>/libtorrserver.so y el
 * instalador lo deja en nativeLibraryDir con permiso de ejecucion
 * (android:extractNativeLibs="true"). Ejecutar binarios descargados en
 * filesDir esta prohibido por SELinux en Android moderno.
 *
 * El binario es software de terceros (GPLv3) que corre como proceso
 * separado y habla con la app solo por HTTP en localhost: no se enlaza
 * con este codigo.
 */
class TorrServerBinary(context: Context) {

    private val appContext: Context = context.applicationContext

    private var process: Process? = null
    private val lifecycleMutex = Mutex()

    private val http = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    val baseUrl: String get() = "http://127.0.0.1:$PORT"

    private val binaryFile: File
        get() = File(appContext.applicationInfo.nativeLibraryDir, "libtorrserver.so")

    private val configDir: File
        get() = File(appContext.filesDir, "torrserver").also { it.mkdirs() }

    fun isRunning(): Boolean {
        return try {
            val request = Request.Builder().url("$baseUrl/echo").build()
            http.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deja el demonio corriendo. Idempotente y seguro ante llamadas
     * concurrentes: si ya responde, no hace nada.
     */
    suspend fun ensureRunning() = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            if (isRunning()) return@withContext
            // Huerfano de una sesion anterior (app matada): pedirle que pare.
            runCatching {
                Request.Builder().url("$baseUrl/shutdown").build()
                    .let { http.newCall(it).execute().close() }
                Thread.sleep(1000)
            }
            val file = binaryFile
            if (!file.exists()) {
                throw IllegalStateException("Falta libtorrserver.so para este ABI en ${file.absolutePath}")
            }
            if (!file.canExecute()) file.setExecutable(true)
            Log.i(TAG, "arrancando TorrServer $VERSION en puerto $PORT")
            val proc = ProcessBuilder(file.absolutePath, "--port", PORT.toString(), "--path", configDir.absolutePath)
                .directory(configDir)
                .redirectErrorStream(true)
                .start()
            process = proc
            Thread({
                try {
                    proc.inputStream.bufferedReader().forEachLine { Log.d(TAG, "[torrserver] $it") }
                } catch (_: Exception) {
                }
            }, "torrserver-log").apply { isDaemon = true; start() }

            val deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (isRunning()) {
                    Log.i(TAG, "TorrServer en marcha")
                    return@withContext
                }
                if (!isAlive(proc)) {
                    process = null
                    throw IllegalStateException(
                        "TorrServer murio al arrancar (exit=${runCatching { proc.exitValue() }.getOrDefault(-1)})",
                    )
                }
                delay(HEALTH_CHECK_INTERVAL_MS)
            }
            stopLocked()
            throw IllegalStateException("TorrServer no responde tras ${STARTUP_TIMEOUT_MS / 1000}s")
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock { stopLocked() }
    }

    private fun stopLocked() {
        runCatching {
            Request.Builder().url("$baseUrl/shutdown").build().let { http.newCall(it).execute().close() }
        }
        process?.let { proc ->
            try {
                Thread.sleep(2000)
                if (isAlive(proc)) destroyProcess(proc)
            } catch (_: Exception) {
                runCatching { destroyProcess(proc) }
            }
        }
        process = null
    }

    private fun isAlive(proc: Process?): Boolean {
        if (proc == null) return false
        return try {
            proc.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun destroyProcess(proc: Process) {
        // destroyForcibly() exige API 26; el minSdk es 24.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            proc.destroyForcibly()
        } else {
            proc.destroy()
        }
    }

    companion object {
        private const val TAG = "TorrServerBinary"
        const val VERSION = "MatriX.144.1"
        // Puerto propio (Nuvio/Stremio usan el 8091): dos apps con TorrServer
        // no deben compartir demonio ni matarse el proceso entre si.
        const val PORT = 8092
        private const val STARTUP_TIMEOUT_MS = 15_000L
        private const val HEALTH_CHECK_INTERVAL_MS = 200L
    }
}
