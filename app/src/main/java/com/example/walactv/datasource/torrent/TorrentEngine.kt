package com.example.walactv.datasource.torrent

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
)

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
        private const val MAX_PREPARE_PIECES = 16
        private const val TAIL_PREPARE_PIECES = 6
        private const val PIECE_WAIT_TIMEOUT_MS = 45_000L
        private const val PIECE_POLL_MS = 250L
        private const val METADATA_WAIT_TIMEOUT_MS = 15_000L
        private const val MIN_BUFFER_BYTES = 600L * 1024 * 1024   // minimo para la ventana de arranque
        private const val RECYCLE_MIN_FREE_BYTES = 1_200_000_000L // reciclar si queda menos libre
        private const val RECYCLE_MIN_INTERVAL_MS = 4 * 60_000L   // entre reciclados
        private const val RECYCLE_MIN_FRACTION_STEP = 0.08f       // y que la reproduccion haya avanzado
        private const val AHEAD_PIECES = 256                      // ventana de descarga hacia delante (~500MB con piezas de 2MB)

        /** Trackers publicos para descubrimiento rapido de peers (ademas de DHT). */
        private val TORRENT_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.tiny-vps.com:6969/announce",
        )
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

    @Volatile private var metadataLatch = CountDownLatch(1)
    private val metadataReceived = AtomicBoolean(false)
    @Volatile private var resumeFractionRequested: Float? = null
    @Volatile private var circularStartPiece = 0
    private var lastRecycleMs = 0L
    private var lastRecycleFraction = 0f

    // Generacion por stream: el onDestroyView de un PlayerFragment viejo puede
    // ejecutarse DESPUES de que el nuevo arranco su torrent (fallback); con la
    // generacion evitamos que el viejo pare el torrent del nuevo.
    private val streamGeneration = java.util.concurrent.atomic.AtomicLong(0)

    // ── Estadisticas para la pantalla de carga ───────────────────────────────
    private val _stats = MutableStateFlow<TorrentStats?>(null)
    val stats: StateFlow<TorrentStats?> = _stats
    private val statsRunning = AtomicBoolean(false)
    @Volatile private var prebufferBytes = 0L
    @Volatile private var prebufferPieceCount = 0
    @Volatile private var pieceLen = 0
    @Volatile private var headRange: IntRange = IntRange.EMPTY
    @Volatile private var tailRange: IntRange = IntRange.EMPTY
    @Volatile private var lastGradientPiece = -1
    @Volatile private var lastGradientMs = 0L

    private val alertListener = object : AlertListener {
        override fun types(): IntArray = intArrayOf(
            AlertType.METADATA_RECEIVED.swig(),
            AlertType.METADATA_FAILED.swig(),
            AlertType.FILE_ERROR.swig(),
            AlertType.TORRENT_ERROR.swig(),
        )

        override fun alert(a: Alert<*>?) {
            when (a) {
                is MetadataReceivedAlert -> onMetadataReceived()
                is org.libtorrent4j.alerts.FileErrorAlert ->
                    onStorageError("Error de disco: ${a.message()}")
                is org.libtorrent4j.alerts.TorrentErrorAlert ->
                    onStorageError("Error del torrent: ${a.message()}")
                else -> if (a?.type() == AlertType.METADATA_FAILED) {
                    Log.e(TAG, "metadata failed para ${activeInfoHash.orEmpty()}")
                    metadataLatch.countDown()
                    listeners.forEach { it.onError("No se pudieron obtener los metadatos del torrent") }
                }
            }
        }
    }

    /** Error de disco/almacenamiento (p.ej. ENOSPC con torrents grandes en
     *  TVs con poca memoria): sin esto el download seguia "descargando" y
     *  sirviendo ceros infinitamente. Notifica para saltar de fuente. */
    private fun onStorageError(detail: String) {
        if (activeInfoHash == null) return
        Log.e(TAG, "storage error: $detail")
        listeners.forEach { it.onError(detail) }
    }

    fun addListener(listener: Listener) = listeners.add(listener)
    fun removeListener(listener: Listener) = listeners.remove(listener)

    /** Progreso global de descarga del stream actual (0..1). */
    fun downloadProgress(): Float = runCatching { handle?.status()?.progress() ?: 0f }.getOrDefault(0f)

    /** Diagnostico del torrent activo: peers, seeds y velocidad. */
    fun debugStatus(): String = runCatching {
        val s = handle?.status() ?: return "sin handle"
        "peers=${s.numPeers()} seeds=${s.numSeeds()} rate=${s.downloadPayloadRate()}B/s " +
            "progress=${"%.1f".format(s.progress() * 100)}%"
    }.getOrDefault("status n/d")

    fun isStreaming(): Boolean = activeInfoHash != null

    fun currentInfoHash(): String? = activeInfoHash

    /** Directorio donde libtorrent materializa los archivos (por hash para no pisar otro torrent en fallback). */
    private fun saveDirectory(hash: String? = null): File {
        val h = hash ?: activeInfoHash
        return if (h != null) File(appContext.cacheDir, "torrent/$h").apply { mkdirs() }
        else File(appContext.cacheDir, "torrent").apply { mkdirs() }
    }

    /**
     * Arranca la descarga de un magnet. Devuelve inmediatamente; el callback
     * [Listener.onReady] se invoca cuando el archivo de video esta listo.
     *
     * [resumeFraction] (0..1): modo buffer circular. Se IGNORA todo el
     * contenido anterior a ese punto y solo se descarga la ventana alrededor
     * de la posicion actual (usado por maybeRecycle para borrar lo ya visto).
     */
    fun startStream(infoHash: String, fileIdx: Int? = null, resumeFraction: Float? = null) {
        val hash = infoHash.trim().lowercase()
        require(hash.length == 40 && hash.all { it.isDigit() || it in 'a'..'f' }) {
            "infoHash torrent invalido"
        }
        // Idempotencia: si ya estamos descargando/reproduciendo el mismo
        // hash y los metadatos ya estan listos, no borrar a mitad de
        // descarga (antes provocaba "Obteniendo metadatos…" a mitad).
        if (hash == activeInfoHash && info != null && handle?.isValid() == true) {
            Log.d(TAG, "startStream: mismo hash ya con metadatos, reutilizando sin reiniciar")
            isReady = true
            metadataLatch.countDown()
            _stats.value = _stats.value?.copy(metadataReady = true) ?: TorrentStats(
                metadataReady = true, peers = 0, seeds = 0,
                downloadedBytes = 0, totalBytes = selectedFileSize, rateBytesPerSec = 0,
                etaSeconds = 0, progressPercent = 0,
            )
            startStatsLoop()
            return
        }
        // Al cambiar de torrent (fallback de codec) no borrar inmediatamente el
        // anterior: el player viejo puede tener un Range en vuelo que llegaría
        // 404 y dispararía bucle de re-prepares. Se limpia bajo demanda.
        stopStream(clearFiles = false)
        Log.d(TAG, "startStream: $hash fileIdx=$fileIdx")

        activeInfoHash = hash
        isReady = false
        streamGeneration.incrementAndGet()
        selectedFileIndex = -1
        selectedFileSize = 0L
        prebufferBytes = 0L
        metadataReceived.set(false)
        resumeFractionRequested = resumeFraction?.coerceIn(0f, 1f)
        if (fileIdx != null) selectedFileIndex = fileIdx
        // Latch nuevo por stream: el anterior pudo quedar liberado por stopStream
        metadataLatch = CountDownLatch(1)
        // Pantalla de carga: fase "buscando fuentes" hasta que lleguen metadatos
        _stats.value = TorrentStats(
            metadataReady = false, peers = 0, seeds = 0,
            downloadedBytes = 0, totalBytes = 0, rateBytesPerSec = 0,
            etaSeconds = null, progressPercent = 0,
        )
        startStatsLoop()

        val mgr = session ?: createSession()
        // Un solo registro del listener: antes se anadia en cada startStream
        // y onMetadataReceived se ejecutaba N veces tras N fallbacks.
        runCatching { mgr.removeListener(alertListener) }
        mgr.addListener(alertListener)

        val magnet = buildString {
            append("magnet:?xt=urn:btih:$hash")
            // Sin trackers el descubrimiento depende solo de DHT y puede tardar
            // minutos; la cola del MKV se necesita a los pocos segundos.
            for (tr in TORRENT_TRACKERS) {
                append("&tr=").append(java.net.URLEncoder.encode(tr, "UTF-8"))
            }
        }
        thread(name = "walac-torrent-start") {
            try {
                // SEQUENTIAL: el cuerpo baja en orden -> se reproduce mientras
                // descarga y la tasa de video nunca depende de piezas sueltas.
                // Cabeza, cola (Cues) y ventana de reanudacion se priorizan con
                // deadlines puntuales en onMetadataReceived/applyGradientAt.
                // Espera por POLLING de torrentFile() (no solo el latch): si el
                // alert se pierde o el torrent ya tenia metadatos, el await se
                // colgaba 30s y dejaba el overlay clavado en "Obteniendo
                // metadatos…" sin reintento. Hasta 3 intentos de re-add: el
                // primero suele estancarse con la sesion fria (DHT sin
                // bootstrapear) y el reintento recibe metadatos al instante.
                var metadataOk = false
                for (attempt in 1..3) {
                    if (attempt > 1) {
                        if (activeInfoHash != hash) return@thread
                        Log.w(TAG, "startStream: reintento $attempt de descarga de metadatos")
                        runCatching {
                            mgr.find(org.libtorrent4j.Sha1Hash.parseHex(hash))?.let { mgr.remove(it) }
                        }
                        mgr.download(magnet, saveDirectory(hash), TorrentFlags.SEQUENTIAL_DOWNLOAD)
                    }
                    val deadline = System.currentTimeMillis() + METADATA_WAIT_TIMEOUT_MS
                    while (System.currentTimeMillis() < deadline) {
                        if (activeInfoHash != hash) return@thread // reemplazado/parado
                        if (metadataReceived.get()) {
                            metadataOk = true
                            break
                        }
                        val ti = runCatching {
                            mgr.find(org.libtorrent4j.Sha1Hash.parseHex(hash))?.torrentFile()
                        }.getOrNull()
                        if (ti != null) {
                            // El alert no llego (o llego antes de tiempo):
                            // configurar prioridades aqui mismo (idempotente).
                            onMetadataReceived()
                            metadataOk = true
                            break
                        }
                        Thread.sleep(1_000)
                    }
                    if (metadataOk) break
                }
                if (!metadataOk) {
                    Log.e(TAG, "startStream: timeout esperando metadatos tras reintento")
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
        // always_pwrite: las piezas se escriben al fichero al completarse.
        // Con el modo mmap por defecto (libtorrent 2.x) "havePiece" puede ser
        // true mientras los datos aun no estan en el fichero y RandomAccessFile
        // lee el archivo atrasado/roto -> el extractor MKV nunca parsea los
        // Cues y la reproduccion no arranca hasta bajarlo entero.
        val sp = org.libtorrent4j.SettingsPack()
            .setInteger(
                org.libtorrent4j.swig.settings_pack.int_types.disk_io_write_mode.swigValue(),
                org.libtorrent4j.swig.settings_pack.mmap_write_mode_t.always_pwrite.swigValue(),
            )
            .setInteger(
                org.libtorrent4j.swig.settings_pack.int_types.disk_io_read_mode.swigValue(),
                org.libtorrent4j.swig.settings_pack.mmap_write_mode_t.always_pwrite.swigValue(),
            )
            .setInteger(org.libtorrent4j.swig.settings_pack.int_types.mmap_file_size_cutoff.swigValue(), 0)
        // Routers DHT: sin ellos el primer bootstrap tarda minutos y los
        // metadatos no llegan (overlay clavado en "Obteniendo metadatos…").
        runCatching {
            sp.setString(
                org.libtorrent4j.swig.settings_pack.string_types.dht_bootstrap_nodes.swigValue(),
                "dht.libtorrent.org:25401,router.bittorrent.com:6881,router.utorrent.com:6881,dht.transmissionbt.com:6881",
            )
        }
        mgr.start(org.libtorrent4j.SessionParams(sp))
        session = mgr
        return mgr
    }

    private fun onMetadataReceived() {
        // Idempotente: el alert y el polling de startStream pueden invocarlo
        // a la vez (o dos veces con listeners duplicados).
        if (!metadataReceived.compareAndSet(false, true)) return
        val mgr = session
        val hash = activeInfoHash
        if (mgr == null || hash == null) {
            // Stream parado antes de procesar: permitir reintento futuro
            metadataReceived.set(false)
            return
        }
        val found = try {
            mgr.find(org.libtorrent4j.Sha1Hash.parseHex(hash))
        } catch (e: Exception) {
            Log.e(TAG, "onMetadataReceived: find fallo", e)
            null
        }
        if (found == null || !found.isValid()) {
            Log.e(TAG, "onMetadataReceived: handle invalido para $hash")
            metadataLatch.countDown()
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
            metadataLatch.countDown()
            listeners.forEach { it.onError("El torrent no contiene archivos de video reproducibles") }
            return
        }
        selectedFileIndex = fileIdx
        selectedFileSize = fs.fileSize(fileIdx)
        Log.d(TAG, "onMetadataReceived: archivo seleccionado #$fileIdx '${fs.fileName(fileIdx)}' size=$selectedFileSize")

        // Espacio libre minimo para poderbufferar: si no llega ni para la
        // ventana de arranque, fallar YA (con ENOSPC el archivo queda a ceros
        // y el player se queda negro para siempre). Si hay para la ventana,
        // el modo circular (maybeRecycle) mantiene el uso acotado aunque el
        // archivo sea mas grande que el disco.
        val freeBytes = saveDirectory(hash).usableSpace
        val minBufferBytes = MIN_BUFFER_BYTES
        if (freeBytes < minBufferBytes) {
            val libresMb = freeBytes / (1024 * 1024)
            Log.e(TAG, "onMetadataReceived: almacenamiento insuficiente (${libresMb}MB libres)")
            metadataLatch.countDown()
            listeners.forEach {
                it.onError("Almacenamiento insuficiente: ${libresMb}MB libres")
            }
            return
        }

        // Priorizar solo el archivo elegido. Los demas a LOW (no IGNORE):
        // la ultima pieza del video suele COMPARTIRSE con el archivo siguiente
        // (portada/sample); con IGNORE esa pieza nunca se completa y los Cues
        // del final del MKV no se pueden leer -> sin primer fotograma.
        for (i in 0 until fs.numFiles()) {
            found.filePriority(i, if (i == fileIdx) Priority.TOP_PRIORITY else Priority.LOW)
        }

        val pieceLength = ti.pieceLength()
        val numPieces = ti.numPieces()

        // ── Modo buffer circular: reanudar desde resumeFraction ────────────
        // Todo lo ANTERIOR al punto de reanudacion va a IGNORE: el disco solo
        // crece con lo que se ve hacia adelante. maybeRecycle borra el archivo
        // y re-descarga desde la posicion actual cuando toca.
        val fileOffset0 = try { fs.fileOffset(fileIdx) } catch (_: Exception) { 0L }
        var startPiece = 0
        val resume = resumeFractionRequested
        if (resume != null && resume > 0.005f) {
            startPiece = ((fileOffset0 + selectedFileSize * resume) / pieceLength)
                .toInt().coerceIn(0, numPieces - 1)
        }
        circularStartPiece = startPiece
        if (startPiece > 0) {
            for (i in 0 until startPiece) {
                runCatching { found.piecePriority(i, Priority.IGNORE) }
            }
            // El extractor siempre sondea la CABEZA (EBML header en offset 0):
            // las primeras piezas se necesitan aunque se reanude a mitad.
            val headPieces = minOf(2, numPieces)
            for (i in 0 until headPieces) {
                runCatching {
                    found.setPieceDeadline(i, 0)
                    found.piecePriority(i, Priority.TOP_PRIORITY)
                }
            }
            Log.d(TAG, "onMetadataReceived: buffer circular desde pieza $startPiece (resume=${"%.2f".format(resume)}); [0,$startPiece) a IGNORE")
        }

        // Prioridad alta a la ventana de arranque para arrancar rapido
        val preparePieces = (selectedFileSize / pieceLength).toInt().coerceIn(MIN_PREPARE_PIECES, MAX_PREPARE_PIECES)
        val windowStart = startPiece
        val windowEnd = (startPiece + preparePieces).coerceAtMost(numPieces)
        for (i in windowStart until windowEnd) {
            found.setPieceDeadline(i, 2_000)
            found.piecePriority(i, Priority.TOP_PRIORITY)
        }
        // Priorizar tambien la COLA del archivo: el extractor de Media3 sondea el
        // final del MKV (Cues) justo despues de abrir, y con descarga secuencial
        // esas piezas tardarian horas -> timeout y error fatal de reproduccion.
        // Cobertura generosa: hasta el 2% del archivo o 20 piezas (los Cues de
        // un MKV grande pueden ocupar mas de 6 piezas).
        val fileOffset = fileOffset0
        val lastPiece = ((fileOffset + selectedFileSize - 1) / pieceLength).toInt()
            .coerceAtMost(numPieces - 1)
        val fileFirstPiece = (fileOffset / pieceLength).toInt()
        val filePieceCount = (lastPiece - fileFirstPiece + 1).coerceAtLeast(1)
        val tailByPercent = (filePieceCount * 0.02f).toInt()
        val tailCount = maxOf(tailByPercent, TAIL_PREPARE_PIECES, 1).coerceAtMost(20)
        val tailStart = (lastPiece - tailCount + 1).coerceAtLeast(windowEnd)
        for (i in tailStart..lastPiece) {
            val err = runCatching {
                found.setPieceDeadline(i, 2_000)
                found.piecePriority(i, Priority.TOP_PRIORITY)
            }.exceptionOrNull()
            if (err != null) Log.w(TAG, "onMetadataReceived: prioridad cola pieza $i fallo: ${err.message}")
        }
        // Rangos del prebuffer (cabeza+cola) para el progreso/ETA de la pantalla de carga
        pieceLen = pieceLength
        headRange = windowStart until windowEnd
        tailRange = tailStart..lastPiece
        prebufferPieceCount = headRange.count() + tailRange.count()
        prebufferBytes = (prebufferPieceCount.toLong() * pieceLength).coerceAtMost(selectedFileSize)
        Log.d(TAG, "onMetadataReceived: priorizadas cabeza=$headRange cola=$tailRange")

        // Gradiente inicial desde la pieza de arranque (no desde 0)
        if (startPiece > 0) {
            applyGradientAt(startPiece)
        }

        listeners.forEach { it.onMetadataReady(fs.numFiles()) }
        isReady = true
        metadataLatch.countDown()
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
        var nextRefresh = 0L
        var statusLogged = false
        while (System.currentTimeMillis() < deadline) {
            if (!h.isValid()) return false
            try {
                if (h.havePiece(pieceIndex)) return true
                // Pieza fuera de orden (seek/cola/reanudacion): pedir ya con
                // deadline 0 (inmediato). Refrescar cada 5s mientras siga
                // pendiente para mantenerla al frente de la cola.
                if (System.currentTimeMillis() >= nextRefresh) {
                    runCatching { h.setPieceDeadline(pieceIndex, 0) }
                    nextRefresh = System.currentTimeMillis() + 5_000
                }
            } catch (_: Exception) {
                return false
            }
            // Diagnostico una sola vez por espera, 15s antes del timeout
            if (!statusLogged && System.currentTimeMillis() > deadline - 15_000) {
                Log.w(TAG, "awaitPiece($pieceIndex): sin pieza — ${debugStatus()}")
                statusLogged = true
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
        val ti = info ?: return null
        val pieceLength = (ti.pieceLength() ?: 0).toLong()
        if (pieceLength <= 0) return null
        // Offset del archivo DENTRO del torrent: si el video no es el archivo
        // #0 (sample/extra delante), el indice de pieza es sobre el TORRENT,
        // no sobre el archivo. Sin esto se esperaban piezas equivocadas (las
        // del inicio, prioridad baja) y la lectura de reanudacion colgaba.
        val fileOffset = try { ti.files().fileOffset(selectedFileIndex) } catch (_: Exception) { 0L }

        // Esperar a que la primera pieza del rango este disponible. El gradiente
        // de prioridades sigue la posicion de lectura: asi el arranque y los
        // seeks bajan primero las piezas que el extractor pide (Stremio-like).
        val firstPiece = ((fileOffset + offset) / pieceLength).toInt()
        followReadPosition(firstPiece)
        if (!awaitPieceAvailable(firstPiece)) {
            Log.w(TAG, "readRange: timeout esperando pieza $firstPiece en offset $offset (fileOffset=$fileOffset)")
            return null
        }

        return try {
            RandomAccessFile(File(path), "r").use { raf ->
                raf.seek(offset)
                val buffer = ByteArray(length)
                var read = 0
                while (read < length) {
                    // Si cruzamos a una pieza nueva, esperar a que este lista
                    val currentPiece = ((fileOffset + offset + read) / pieceLength).toInt()
                    if (currentPiece != firstPiece && !awaitPieceAvailable(currentPiece)) {
                        Log.w(TAG, "readRange: timeout esperando pieza $currentPiece (parcial $read/$length)")
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

    /** Reordena prioridades para descargar linealmente desde [fraction] (0..1). */
    fun prioritizePosition(fraction: Float) {
        val ti = info ?: return
        val pieceLength = ti.pieceLength()
        if (pieceLength <= 0) return
        val numPieces = ti.numPieces()
        val fileOffset = try { ti.files().fileOffset(selectedFileIndex) } catch (_: Exception) { 0L }
        // Clamp: con duration == TIME_UNSET (extractor aun parseando) el
        // fraction podia salir negativo y priorizar la pieza 0 equivocado.
        val clamped = fraction.coerceIn(0f, 1f)
        val p = ((fileOffset + selectedFileSize * clamped) / pieceLength)
            .toInt()
            .coerceIn(0, numPieces - 1)
        applyGradientAt(p)
    }

    /** Ventana TOP en [p] con deadlines escalonados (las ~32 piezas siguientes
     *  al playhead se piden en paralelo y en orden, como Stremio), gradiente
     *  decreciente hacia el final; lo anterior a [p] a prioridad minima. */
    private fun applyGradientAt(p: Int) {
        val h = handle ?: return
        val ti = info ?: return
        val numPieces = ti.numPieces()
        val window = (MAX_PREPARE_PIECES * 2).coerceAtLeast(16)
        // Ventana de reproduccion: deadlines escalonados para que lleguen rapido
        // y en orden (sin esto, cada pieza esperaba su turno y el primer
        // fotograma se retrasaba decenas de segundos).
        val windowEnd = (p + window).coerceAtMost(numPieces)
        var windowErrors = 0
        for (i in p until windowEnd) {
            val err = runCatching {
                // Deadline 0 = pedir ya, no "para dentro de X ms": con deadlines
                // escalonados libtorrent entregaba cada pieza justo a su tiempo
                // (~10s/pieza) y el primer fotograma tardaba minutos.
                h.setPieceDeadline(i, 0)
                h.piecePriority(i, Priority.TOP_PRIORITY)
            }.exceptionOrNull()
            if (err != null) {
                windowErrors++
                if (windowErrors <= 3 || i == p) {
                    Log.w(TAG, "applyGradientAt: deadline pieza $i fallo: ${err.message}")
                }
            }
        }
        if (windowErrors > 3) {
            Log.w(TAG, "applyGradientAt: $windowErrors/$window piezas con fallo de deadline (p=$p numPieces=$numPieces)")
        }
        // Gradiente decreciente SOLO dentro de la ventana de descarga: mas
        // alla de AHEAD_PIECES, IGNORE (se iran activando al avanzar). Sin
        // este tope libtorrent descargaba el archivo entero por adelantado.
        val aheadLimit = (p + AHEAD_PIECES).coerceAtMost(numPieces)
        for (i in windowEnd until aheadLimit) {
            val distance = i - p
            val pr = when {
                distance < window * 2 -> Priority.SIX
                distance < window * 4 -> Priority.FIVE
                distance < window * 8 -> Priority.DEFAULT
                distance < window * 16 -> Priority.THREE
                distance < window * 32 -> Priority.TWO
                else -> Priority.LOW
            }
            runCatching { h.piecePriority(i, pr) }
        }
        for (i in aheadLimit until numPieces) {
            if (tailRange.contains(i)) continue
            if (i < circularStartPiece) continue
            runCatching { h.piecePriority(i, Priority.IGNORE) }
        }
        // Contenido anterior al punto de lectura: al final de la cola. En modo
        // circular, lo anterior a la pieza de arranque se mantiene IGNORE.
        for (i in 0 until p) {
            if (headRange.contains(i) || tailRange.contains(i)) continue
            if (i < circularStartPiece) {
                runCatching { h.piecePriority(i, Priority.IGNORE) }
                continue
            }
            runCatching { h.piecePriority(i, Priority.LOW) }
        }
        lastGradientPiece = p
        lastGradientMs = System.currentTimeMillis()
        Log.d(TAG, "applyGradientAt: pieza=$p window=$window")
    }

    /** Gradiente siguiendo la posicion de lectura del extractor (throttle 10s
     *  o si se mueve media ventana). Llamar desde readRange. */
    private fun followReadPosition(firstPiece: Int) {
        val now = System.currentTimeMillis()
        val window = (MAX_PREPARE_PIECES * 2).coerceAtLeast(16)
        val moved = lastGradientPiece < 0 ||
            kotlin.math.abs(firstPiece - lastGradientPiece) > window / 2
        if (moved || now - lastGradientMs > 10_000) {
            applyGradientAt(firstPiece.coerceAtLeast(0))
        }
    }

    fun selectedFileSize(): Long = selectedFileSize

    /** Bucle de 1s que publica [TorrentStats] mientras hay un stream activo. */
    private fun startStatsLoop() {
        if (!statsRunning.compareAndSet(false, true)) return
        var lastLogMs = 0L
        thread(name = "walac-torrent-stats", isDaemon = true) {
            while (statsRunning.get()) {
                val st = currentStats()
                _stats.value = st
                // Diagnostico cada 10s para depurar arranques lentos en TV
                val now = System.currentTimeMillis()
                if (now - lastLogMs > 10_000) {
                    lastLogMs = now
                    Log.d(TAG, "stats: ${st.progressPercent}% ${st.downloadedBytes / (1024 * 1024)}MB " +
                        "rate=${st.rateBytesPerSec / 1024}KB/s seeds=${st.seeds} peers=${st.peers} " +
                        "eta=${st.etaSeconds ?: "-"} prebufferTarget=${prebufferBytes / (1024 * 1024)}MB")
                }
                try {
                    Thread.sleep(1_000)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun currentStats(): TorrentStats {
        if (!isStreaming()) {
            return TorrentStats(false, 0, 0, 0, 0, 0, null, 0)
        }
        val s = runCatching { handle?.status() }.getOrNull()
            ?: return TorrentStats(isReady, 0, 0, 0, 0, 0, null, 0)
        val done = runCatching { s.totalWantedDone() }.getOrDefault(0L)
        val wanted = runCatching { s.totalWanted() }.getOrDefault(0L)
        val rate = runCatching { s.downloadPayloadRate() }.getOrDefault(0).toLong()
        val peers = runCatching { s.numPeers() }.getOrDefault(0)
        val seeds = runCatching { s.numSeeds() }.getOrDefault(0)

        // Progreso del PREBUFFER (cabeza+cola): lo que de verdad falta para
        // empezar a reproducir, no el archivo entero.
        var have = 0
        val h = handle
        if (h != null && headRange != IntRange.EMPTY) {
            for (i in headRange) {
                if (runCatching { h.havePiece(i) }.getOrDefault(false)) have++
            }
            for (i in tailRange) {
                if (runCatching { h.havePiece(i) }.getOrDefault(false)) have++
            }
        }
        val remainingBytes = ((prebufferPieceCount - have).coerceAtLeast(0)).toLong() * pieceLen
        return TorrentStats(
            metadataReady = isReady,
            peers = peers,
            seeds = seeds,
            downloadedBytes = done,
            totalBytes = wanted,
            rateBytesPerSec = rate,
            etaSeconds = if (remainingBytes > 0 && rate > 0) {
                ((remainingBytes / rate).toInt() + 1).coerceIn(1, 600)
            } else {
                0
            },
            progressPercent = if (prebufferPieceCount > 0) (have * 100 / prebufferPieceCount) else 0,
        )
    }

    fun selectedFileName(): String? {
        val ti = info ?: return null
        val idx = selectedFileIndex
        if (idx < 0 || idx >= ti.files().numFiles()) return null
        return ti.files().fileName(idx)
    }

    fun stopStream(clearFiles: Boolean = true) {
        val doClearHash = activeInfoHash
        Log.d(TAG, "stopStream hash=$doClearHash clearFiles=$clearFiles")
        statsRunning.set(false)
        _stats.value = null
        metadataReceived.set(false)
        resumeFractionRequested = null
        circularStartPiece = 0
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
        if (clearFiles) {
            runCatching {
                if (doClearHash != null) saveDirectory(doClearHash).deleteRecursively()
                else File(appContext.cacheDir, "torrent").deleteRecursively()
            }
        }
    }

    /** Generacion actual del stream ( cambia en cada startStream ). */
    fun currentStreamGeneration(): Long = streamGeneration.get()

    /**
     * Buffer circular: cuando el espacio libre baja del umbral (y la
     * reproduccion ha avanzado desde el ultimo reciclado), borra el archivo
     * del torrent —libera TODO su espacio— y re-descarga desde la fraccion
     * actual. El player solo nota un rebuffer breve; su re-prepare reanuda en
     * la misma posicion una vez que el engine esta listo.
     */
    fun maybeRecycle(fraction: Float) {
        val hash = activeInfoHash ?: return
        if (!isReady) return
        val now = System.currentTimeMillis()
        if (now - lastRecycleMs < RECYCLE_MIN_INTERVAL_MS) return
        val frac = fraction.coerceIn(0f, 1f)
        if (frac - lastRecycleFraction < RECYCLE_MIN_FRACTION_STEP) return
        val free = saveDirectory(hash).usableSpace
        if (free > RECYCLE_MIN_FREE_BYTES) return
        val fileIdx = selectedFileIndex
        val librasGb = "%.1f".format(free / 1e9)
        Log.w(TAG, "maybeRecycle: ${librasGb}GB libres, frac=${"%.2f".format(frac)} — borrando buffer y re-descargando desde aqui")
        lastRecycleMs = now
        lastRecycleFraction = frac
        stopStream(clearFiles = true)
        startStream(hash, if (fileIdx >= 0) fileIdx else null, frac)
    }

    /** Para el torrent SOLO si el llamador sigue siendo el dueno activo. */
    fun stopStreamIfOwner(ownerGeneration: Long, clearFiles: Boolean = true) {
        if (streamGeneration.get() == ownerGeneration) {
            stopStream(clearFiles)
        } else {
            Log.d(
                TAG,
                "stopStreamIfOwner: ignorado (owner=$ownerGeneration, activo=${streamGeneration.get()}) — el torrent actual pertenece a otro player",
            )
        }
    }

    fun destroy() {
        stopStream()
        session?.removeListener(alertListener)
        runCatching { session?.swig()?.abort() }
        session = null
        httpServer?.stop()
        httpServer = null
    }

    // ── Servidor HTTP local (patron Stremio/Nuvio: Media3 lee por HTTP con
    //    Range headers y el servidor bloquea hasta tener la pieza) ────────────

    @Volatile private var httpServer: TorrentHttpServer? = null

    /** URL local para reproducir el torrent activo via HTTP con rangos. */
    fun localStreamUrl(): String {
        val hash = activeInfoHash ?: return ""
        val server = httpServer ?: TorrentHttpServer(this).also {
            it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            httpServer = it
        }
        return "http://127.0.0.1:${server.listeningPort}/stream/$hash"
    }

    private inner class TorrentHttpServer(private val engine: TorrentEngine) : NanoHTTPD("127.0.0.1", 0) {

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri.removePrefix("/stream/")
            val hash = uri.substringBefore('/').ifBlank { null }
            if (hash == null) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "torrent no activo")
            }
            // Solo se sirve el hash ACTIVO. Peticiones de un hash anterior
            // (player viejo en fallback): 503 con Retry-After para que
            // ExoPlayer reintente sin bucles de errores fatales. Nunca servir
            // datos de otro torrent (sizes/tamano no coinciden).
            if (hash != engine.currentInfoHash()) {
                val r = newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "torrent no activo")
                r.addHeader("Retry-After", "1")
                return r
            }

            // Esperar a que el archivo este listo para leer. RE-VERIFICAR el
            // hash al salir de la espera: isReady puede ponerse true por los
            // metadatos de OTRO torrent (fallback mientras esperabamos) y
            // entonces selectedFileSize/bytes serian del torrent equivocado.
            val servedGeneration = engine.currentStreamGeneration()
            val waitDeadline = System.currentTimeMillis() + 120_000
            while (!engine.isReady) {
                if (hash != engine.currentInfoHash()) {
                    val gone = newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "torrent cambiado durante espera")
                    gone.addHeader("Retry-After", "1")
                    return gone
                }
                if (!engine.isStreaming()) {
                    return newFixedLengthResponse(Response.Status.GONE, "text/plain", "stream detenido")
                }
                if (System.currentTimeMillis() > waitDeadline) {
                    return newFixedLengthResponse(Response.Status.REQUEST_TIMEOUT, "text/plain", "timeout esperando metadatos")
                }
                Thread.sleep(250)
            }
            if (hash != engine.currentInfoHash() || servedGeneration != engine.currentStreamGeneration()) {
                val gone = newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "torrent cambiado durante espera")
                gone.addHeader("Retry-After", "1")
                return gone
            }

            val total = engine.selectedFileSize()
            val rangeHeader = session.headers["range"]
            var start = 0L
            var end = total - 1
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val range = rangeHeader.removePrefix("bytes=").substringBefore(',')
                val parts = range.split('-')
                start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                end = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull() ?: (total - 1)
                start = start.coerceIn(0, total - 1)
                end = end.coerceIn(start, total - 1)
            }

            val input = TorrentInputStream(engine, start, end, servedGeneration)
            android.util.Log.d("TorrentStream", "serve: hash=${hash.take(8)} range=$start-$end total=$total ready=${engine.isReady}")
            val resp = newFixedLengthResponse(
                if (rangeHeader != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
                guessMimeType(engine.selectedFileName()),
                input,
                end - start + 1,
            )
            resp.addHeader("Accept-Ranges", "bytes")
            resp.addHeader("Content-Range", "bytes $start-$end/$total")
            return resp
        }

        private fun guessMimeType(fileName: String?): String {
            val ext = fileName?.substringAfterLast('.', "")?.lowercase() ?: return "application/octet-stream"
            return when (ext) {
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "avi" -> "video/x-msvideo"
                "webm" -> "video/webm"
                "mov" -> "video/quicktime"
                "ts", "mpg", "mpeg" -> "video/mp2t"
                else -> "application/octet-stream"
            }
        }
    }

    private class TorrentInputStream(
        private val engine: TorrentEngine,
        private var offset: Long,
        private var end: Long,
        private val servedGeneration: Long,
    ) : java.io.InputStream() {

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) > 0) one[0].toInt() and 0xFF else -1
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            try {
                // Si el engine cambio de torrent mientras servimos, cortar limpio:
                // seguir leyendo devolveria bytes del archivo equivocado.
                if (servedGeneration != engine.currentStreamGeneration()) {
                    android.util.Log.w("TorrentStream", "read: generacion cambio ($servedGeneration != ${engine.currentStreamGeneration()}), EOF")
                    return -1
                }
                if (offset > end) {
                    android.util.Log.w("TorrentStream", "read: offset=$offset > end=$end, EOF")
                    return -1
                }
                val toRead = minOf(len, (end - offset + 1).toInt()).coerceAtLeast(0)
                if (toRead <= 0) {
                    android.util.Log.w("TorrentStream", "read: toRead=$toRead (len=$len offset=$offset end=$end), EOF")
                    return -1
                }
                var chunk: ByteArray? = null
                // Deadline global sin progreso: si llevamos >90s sin entregar
                // NI un byte, cortar con EOF para que ExoPlayer recupere
                // (re-prepare) en vez de bloquear para siempre.
                val noProgressDeadline = System.currentTimeMillis() + 90_000
                while (chunk == null) {
                    if (!engine.isStreaming()) {
                        android.util.Log.w("TorrentStream", "read: stream detenido en offset=$offset, EOF")
                        return -1
                    }
                    chunk = engine.readRange(offset, toRead)
                    if (chunk == null) {
                        if (System.currentTimeMillis() > noProgressDeadline) {
                            android.util.Log.w("TorrentStream", "read: 90s sin progreso en offset=$offset, EOF para recuperacion")
                            return -1
                        }
                        Thread.sleep(250)
                    }
                }
                val n = minOf(chunk.size, len).coerceAtLeast(1)
                java.lang.System.arraycopy(chunk, 0, b, off, n)
                offset += n
                return n
            } catch (t: Throwable) {
                android.util.Log.e("TorrentStream", "read: excepcion en offset=$offset len=$len", t)
                throw t
            }
        }
    }
}
