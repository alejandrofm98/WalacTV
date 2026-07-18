package com.example.walactv.ui.fragment

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.example.walactv.R
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.compose.ui.platform.ComposeView
import com.bumptech.glide.Glide
import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.model.ContentKind
import com.example.walactv.WalacApp
import com.example.walactv.data.model.PlaybackError
import com.example.walactv.data.model.UnifiedStreamOption
import com.example.walactv.data.remote.api.dto.WatchProgressDto
import com.example.walactv.data.remote.api.dto.shouldRestoreProgress
import com.example.walactv.data.remote.repository.IntroDbRepository
import com.example.walactv.data.remote.repository.IntroDbSegments
import com.example.walactv.data.model.categorizePlaybackError
import com.example.walactv.data.remote.repository.WatchProgressRepository
import com.example.walactv.data.util.languageDisplayLabel
import com.example.walactv.data.util.normalizeLanguageCode
import com.example.walactv.datasource.StreamWishDataSourceFactory
import com.example.walactv.ui.overlay.PlayerErrorOverlay
import com.example.walactv.ui.overlay.isFatalPlaybackErrorForDevice

@UnstableApi
class PlayerFragment : Fragment() {

    private var streamUrl: String = ""
    private var overlayNumber: String = ""
    private var overlayTitle: String = ""
    private var overlayMeta: String = ""
    private var contentKind: ContentKind = ContentKind.MOVIE
    private var onNavigateChannel: ((Int) -> Unit)? = null
    private var onNavigateOption: ((Int) -> Unit)? = null
    private var onDirectChannelNumber: ((Int) -> Boolean)? = null
    private var onToggleFavorite: (() -> Boolean)? = null
    private var onOpenFavorites: (() -> Boolean)? = null
    private var onOpenRecents: (() -> Boolean)? = null
    private var onOpenGuide: ((String?) -> Unit)? = null
    private var onNextEpisode: (() -> Unit)? = null
    private var onPreviousEpisode: (() -> Unit)? = null
    private var allSeriesEpisodes: List<CatalogItem> = emptyList()
    private var currentEpisode: CatalogItem? = null
    private var streamOptionLabels: List<String> = emptyList()
    private var currentOptionIndex: Int = 0
    private var unifiedStreamOptions: List<UnifiedStreamOption> = emptyList()
    private var onSelectUnifiedOption: ((Int) -> Unit)? = null
    private var overlayLogoUrl: String = ""
    private var isFavorite: Boolean = false
    private var contentId: String = ""
    private var _positionMs: Long = 0
    private var onPlayerClosed: (() -> Unit)? = null
    private var onProgressSaved: ((WatchProgressDto) -> Unit)? = null
    private var customHeaders: Map<String, String> = emptyMap()

    init {
        // Default constructor used by Android Fragment system
    }

    fun initialize(
        streamUrl: String,
        overlayNumber: String,
        overlayTitle: String,
        overlayMeta: String,
        contentKind: ContentKind,
        onNavigateChannel: (Int) -> Unit,
        onNavigateOption: (Int) -> Unit,
        onDirectChannelNumber: (Int) -> Boolean,
        onToggleFavorite: () -> Boolean,
        onOpenFavorites: () -> Boolean,
        onOpenRecents: () -> Boolean,
        onOpenGuide: ((String?) -> Unit)? = null,
        onNextEpisode: (() -> Unit)? = null,
        onPreviousEpisode: (() -> Unit)? = null,
        allSeriesEpisodes: List<CatalogItem> = emptyList(),
        currentEpisode: CatalogItem? = null,
        streamOptionLabels: List<String> = emptyList(),
        currentOptionIndex: Int = 0,
        showOptionsOnStart: Boolean = false,
        overlayLogoUrl: String = "",
        isFavorite: Boolean = false,
        contentId: String = "",
        positionMs: Long = 0,
        onPlayerClosed: (() -> Unit)? = null,
        onProgressSaved: ((WatchProgressDto) -> Unit)? = null,
        customHeaders: Map<String, String> = emptyMap(),
        unifiedStreamOptions: List<UnifiedStreamOption> = emptyList(),
        onSelectUnifiedOption: ((Int) -> Unit)? = null,
    ) {
        this.streamUrl = streamUrl
        this.overlayNumber = overlayNumber
        this.overlayTitle = overlayTitle
        this.overlayMeta = overlayMeta
        this.contentKind = contentKind
        this.onNavigateChannel = onNavigateChannel
        this.onNavigateOption = onNavigateOption
        this.onDirectChannelNumber = onDirectChannelNumber
        this.onToggleFavorite = onToggleFavorite
        this.onOpenFavorites = onOpenFavorites
        this.onOpenRecents = onOpenRecents
        this.onOpenGuide = onOpenGuide
        this.onNextEpisode = onNextEpisode
        this.onPreviousEpisode = onPreviousEpisode
        this.allSeriesEpisodes = allSeriesEpisodes
        this.currentEpisode = currentEpisode
        this.currentSeriesEpisode = currentEpisode
        this.advancedToNext = false
        this.currentSegments = null
        this.segmentButtonsHidden = false
        this.streamOptionLabels = streamOptionLabels
        this.currentOptionIndex = currentOptionIndex
        this.liveOptionIndex = currentOptionIndex
        this.shouldShowOptionsOnStart = showOptionsOnStart
        Log.d(TAG, "INITIALIZE: currentOptionIndex=$currentOptionIndex, showOptionsOnStart=$showOptionsOnStart, streamOptionLabels.size=${streamOptionLabels.size}")
        this.overlayLogoUrl = overlayLogoUrl
        this.isFavorite = isFavorite
        this.isFavoriteState = isFavorite
        this.contentId = contentId
        this._positionMs = positionMs
        this.onPlayerClosed = onPlayerClosed
        this.onProgressSaved = onProgressSaved
        this.customHeaders = customHeaders
        this.unifiedStreamOptions = unifiedStreamOptions
        this.onSelectUnifiedOption = onSelectUnifiedOption
    }

    private var currentSeriesEpisode: CatalogItem? = currentEpisode

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var overlayView: LinearLayout
    private lateinit var overlayNumberView: TextView
    private lateinit var overlayTitleView: TextView
    private lateinit var overlayMetaView: TextView
    private lateinit var bottomPanelView: LinearLayout
    private var channelLogoView: ImageView? = null
    private var channelProgressBar: ProgressBar? = null
    private var btnGuide: View? = null
    private var btnFavorites: View? = null
    private var btnFavoritesIcon: ImageView? = null
    private var btnChannel: View? = null
    private var btnChannelLabel: TextView? = null
    private var optionIndicatorView: TextView? = null
    private var optionsListLayout: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private val digitBuffer = StringBuilder()

    private var isFavoriteState: Boolean = false

    private var watchProgressRepo: WatchProgressRepository? = null
    private var lastSavedProgressMs: Long = 0

    private var liveOptionIndex: Int = 0
    private var shouldShowOptionsOnStart: Boolean = false

    private var retryCount: Int = 0
    private var isPlayerInitialized: Boolean = false
    private var isReleasing: Boolean = false
    private var closedByHost: Boolean = false
    private var playerClosed: Boolean = false
    private lateinit var trackSelector: DefaultTrackSelector

    private var watchedMarked = false
    private var advancedToNext = false
    private var introDbRepository: IntroDbRepository? = null
    private var currentSegments: IntroDbSegments? = null
    private var segmentButtonsHidden = false

    private var errorState: PlaybackError? = null
    private var isRetrying: Boolean = false
    private var errorComposeView: ComposeView? = null
    private val bufferingWatchdog = Runnable { handleBufferingTimeout() }
    private var bufferingSinceMs: Long = 0
    private var lastKnownPositionMs: Long = Long.MIN_VALUE
    private var stuckPositionCount: Int = 0
    private val positionWatchdog = Runnable { checkPositionStuck() }

    private fun playbackStateName(state: Int) = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($state)"
    }


    private val isVodMode: Boolean
        get() = contentKind == ContentKind.MOVIE || contentKind == ContentKind.SERIES

    private val isEventMode: Boolean
        get() = contentKind == ContentKind.EVENT

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.player_view, container, false)
        playerView = view.findViewById(R.id.playerView)

        if (isVodMode) {
            watchProgressRepo = (requireActivity().application as WalacApp).appComponent.watchProgressRepository
            setupVodMode()
        } else {
            setupLiveMode(view)
        }

        view.isFocusable = true
        view.isFocusableInTouchMode = true

        return view
    }

    private fun setupVodMode() {
        playerView.useController = true
        playerView.controllerShowTimeoutMs = VOD_CONTROLLER_TIMEOUT_MS
        playerView.controllerAutoShow = true
        playerView.requestFocus()

        if (contentKind == ContentKind.SERIES) {
            introDbRepository = (requireActivity().application as WalacApp).appComponent.introDbRepository
            fetchIntroDbSegments()
        }

        playerView.setShowNextButton(false)
        playerView.setShowPreviousButton(false)

        (playerView.parent as? ViewGroup)?.let { parent ->
            parent.findViewById<View>(R.id.channel_overlay)?.visibility = View.GONE
            parent.findViewById<View>(R.id.player_bottom_panel)?.visibility = View.GONE
        }

        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                if (visibility == View.VISIBLE) {
                    playerView.findViewById<TextView>(R.id.vod_title)?.text = overlayTitle
                    playerView.findViewById<TextView>(R.id.vod_subtitle)?.text = overlayMeta
                    updateVodTimeDisplay()
                }
            },
        )

        playerView.post { playerView.showController() }
    }

    private fun updateVodTimeDisplay() {
        val exoPlayer = player ?: return
        playerView.findViewById<TextView>(R.id.vod_position)?.text = formatTime(exoPlayer.currentPosition)
        playerView.findViewById<TextView>(R.id.vod_duration)?.text = formatTime(exoPlayer.duration)
    }

    private fun setupLiveMode(view: View) {
        playerView.useController = false
        overlayView = view.findViewById(R.id.channel_overlay)
        overlayNumberView = view.findViewById(R.id.channel_number)
        overlayTitleView = view.findViewById(R.id.channel_title)
        overlayMetaView = view.findViewById(R.id.channel_meta)
        bottomPanelView = view.findViewById(R.id.player_bottom_panel)
        channelLogoView = view.findViewById(R.id.channel_logo)
        channelProgressBar = view.findViewById(R.id.channel_progress)
        btnGuide = view.findViewById(R.id.btn_guide)
        btnFavorites = view.findViewById(R.id.btn_favorites)
        btnFavoritesIcon = view.findViewById(R.id.btn_favorites_icon)
        btnChannel = view.findViewById(R.id.btn_channel)
        btnChannelLabel = view.findViewById(R.id.btn_channel_label)

        val overlayLayout = overlayView
        optionIndicatorView = overlayLayout.findViewById(R.id.channel_option_indicator)
        optionsListLayout = overlayLayout.findViewById(R.id.channel_options_list)

        if (isEventMode && streamOptionLabels.size > 1) {
            btnChannel?.visibility = View.VISIBLE
            if (shouldShowOptionsOnStart) {
                showOptionsList()
            }
        }

        bindLiveActionButtons()
        bindOptionIndicator()
        bindOverlay()
        updateFavoriteIcon()
    }

    private fun bindLiveActionButtons() {
        btnGuide?.setOnClickListener {
            Log.d(TAG, "btnGuide CLICK FIRED, onOpenGuide=${onOpenGuide != null}")
            onOpenGuide?.invoke(null)
        }
        btnFavorites?.setOnClickListener {
            val nowFavorite = onToggleFavorite?.invoke() ?: false
            isFavoriteState = nowFavorite
            updateFavoriteIcon()
            showOverlayTemporarily()
        }
        btnChannel?.setOnClickListener {
            showOverlayTemporarily()
            showOptionsList()
        }
    }

    private fun updateFavoriteIcon() {
        btnFavoritesIcon?.setImageResource(
            if (isFavoriteState) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupErrorOverlay(view)
    }

    private fun setupErrorOverlay(view: View) {
        errorComposeView = view.findViewById(R.id.error_overlay)
    }

    override fun onStart() {
        super.onStart()
        if (playerClosed) return
        activity?.findViewById<FrameLayout>(R.id.player_container)?.visibility = View.VISIBLE
        if (!isPlayerInitialized) {
            initializePlayer()
        }
        if (!isVodMode) showOverlayTemporarily()
    }

    override fun onResume() {
        super.onResume()
        if (playerClosed) return
        if (player == null && !isReleasing) {
            Log.d(TAG, "Reinicializando player en onResume")
            initializePlayer()
        }
    }

    private fun initializePlayer() {
        if (isPlayerInitialized || player != null) {
            Log.w(TAG, "Player ya inicializado, liberando antes de recrearlo")
            releasePlayer()
        }

        handler.removeCallbacksAndMessages(null)
        retryCount = 0
        isReleasing = false
        playerClosed = false

        try {
            val isStreamWish = StreamWishDataSourceFactory.isStreamWishUrl(streamUrl)
            val hasCustomHeaders = customHeaders.isNotEmpty()
            Log.d(TAG, "initializePlayer: streamUrl=${streamUrl.take(60)}..., isStreamWish=$isStreamWish, hasCustomHeaders=$hasCustomHeaders, customHeaders=$customHeaders")

            val dataSourceFactory = if (isStreamWish) {
                val referer = extractReferer(streamUrl)
                val origin = "https://${streamUrl.toUri().host}"
                Log.d(TAG, "StreamWish detected: referer=$referer, origin=$origin")
                StreamWishDataSourceFactory(
                    userAgent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.186 Mobile Safari/537.36",
                    referer = referer,
                    origin = origin,
                    connectTimeoutMs = 30_000,
                    readTimeoutMs = 30_000,
                    extraHeaders = mapOf(
                        "Accept" to "*/*",
                        "Accept-Encoding" to "gzip, deflate",
                        "Connection" to "keep-alive",
                    ),
                )
            } else {
                val headers = buildMap {
                    put("Accept", "*/*")
                    put("Accept-Encoding", "gzip, deflate")
                    put("Connection", "keep-alive")
                    putAll(customHeaders)
                }
                Log.d(TAG, "Using DefaultHttpDataSource with headers: ${headers.keys}")
                DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(30_000)
                    .setReadTimeoutMs(30_000)
                    .setUserAgent("WalacTV/AndroidTV")
                    .setDefaultRequestProperties(headers)
            }

            val mediaSourceFactory = DefaultMediaSourceFactory(requireContext())
                .setDataSourceFactory(dataSourceFactory)

            val loadControl = if (isVodMode) {
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 120_000, 2_500, 5_000)
                    .build()
            } else {
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(5_000, 30_000, 1_500, 2_500)
                    .build()
            }

            val renderersFactory = DefaultRenderersFactory(requireContext())
                .setEnableDecoderFallback(true)

            trackSelector = DefaultTrackSelector(requireContext())
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setMaxVideoSize(1920, 1080)
                    .build()
            )

            player = ExoPlayer.Builder(requireContext())
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .build()
                .also { exoPlayer ->
                    playerView.player = exoPlayer

                    // Configurar estilo de subtítulos - texto blanco con fondo transparente
                    playerView.subtitleView?.apply {
                        setStyle(
                            CaptionStyleCompat(
                                Color.WHITE,                      // texto blanco
                                Color.TRANSPARENT,                // fondo transparente
                                Color.TRANSPARENT,                // ventana transparente
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE, // borde negro para legibilidad
                                Color.BLACK,                      // color del borde
                                null                              // fuente por defecto
                            )
                        )
                        // Deshabilitar estilos embebidos del stream para usar nuestro estilo
                        setApplyEmbeddedStyles(false)
                    }

                    exoPlayer.addListener(PlayerListener())
                    exoPlayer.setMediaItem(createMediaItem(streamUrl))
                    exoPlayer.prepare()
                    if (_positionMs > 0L) {
                        Log.d(TAG, "Seeking to saved position: ${_positionMs}ms for $contentId")
                        exoPlayer.seekTo(_positionMs)
                    }
                    exoPlayer.playWhenReady = true
                }

            if (isVodMode) {
                bindVodControls()
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Error al inicializar el player", exception)
            isPlayerInitialized = false
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  VOD controls wiring
    // ──────────────────────────────────────────────────────────────────────

    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            if (player != null && !isReleasing && isVodMode) {
                updateVodTimeDisplay()
                updateSkipButtons()
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val progressSaveRunnable = object : Runnable {
        override fun run() {
            if (player != null && !isReleasing && isVodMode && contentId.isNotBlank()) {
                saveWatchProgress()
                handler.postDelayed(this, PROGRESS_SAVE_INTERVAL_MS)
            }
        }
    }

    private fun bindVodControls() {
        val streamBtn = playerView.findViewById<ImageButton>(R.id.vod_btn_stream)
        if (unifiedStreamOptions.size > 1 && onSelectUnifiedOption != null) {
            streamBtn?.visibility = View.VISIBLE
            streamBtn?.setOnClickListener {
                player?.pause()
                showUnifiedSelector()
            }
        } else {
            streamBtn?.visibility = View.GONE
        }

        // Pausa al abrir subtítulos, reanuda al cerrar el diálogo
        playerView.findViewById<ImageButton>(R.id.vod_btn_subtitles)?.setOnClickListener {
            player?.pause()
            showSubtitleSelector()
        }

        val nextBtn = playerView.findViewById<ImageButton>(R.id.vod_btn_next)
        if (contentKind == ContentKind.SERIES && onNextEpisode != null) {
            nextBtn?.visibility = View.VISIBLE
            nextBtn?.setOnClickListener {
                watchedMarked = false
                advancedToNext = false
                onNextEpisode?.invoke()
            }
        } else {
            nextBtn?.visibility = View.GONE
        }

        val prevBtn = playerView.findViewById<ImageButton>(R.id.vod_btn_prev)
        if (contentKind == ContentKind.SERIES && onPreviousEpisode != null) {
            prevBtn?.visibility = View.VISIBLE
            prevBtn?.setOnClickListener {
                watchedMarked = false
                advancedToNext = false
                onPreviousEpisode?.invoke()
            }
        } else {
            prevBtn?.visibility = View.GONE
        }

        bindSkipButtons()

        handler.removeCallbacks(timeUpdateRunnable)
        handler.post(timeUpdateRunnable)

        if (contentId.isNotBlank()) {
            handler.removeCallbacks(progressSaveRunnable)
            handler.postDelayed(progressSaveRunnable, PROGRESS_SAVE_INTERVAL_MS)
        }
    }

    private fun saveWatchProgress(forceSave: Boolean = false) {
        val exoPlayer = player
        Log.d(TAG, "saveWatchProgress: ENTER contentId='$contentId' isVodMode=$isVodMode player=${exoPlayer != null} lastSavedProgressMs=$lastSavedProgressMs forceSave=$forceSave")
        if (exoPlayer == null) { Log.w(TAG, "saveWatchProgress: player is null, skipping"); return }
        if (contentId.isBlank() || !isVodMode) { Log.w(TAG, "saveWatchProgress: contentId blank or not VOD, skipping"); return }
        val position = exoPlayer.currentPosition
        val duration = exoPlayer.duration
        Log.d(TAG, "saveWatchProgress: position=$position duration=$duration")
        if (duration <= 0 || position <= 0) { Log.w(TAG, "saveWatchProgress: invalid position/duration, skipping"); return }
        if (!forceSave) {
            val delta = kotlin.math.abs(position - lastSavedProgressMs)
            if (delta < 5_000) { Log.d(TAG, "saveWatchProgress: delta=$delta < 5000, skipping"); return }
        }
        lastSavedProgressMs = position
        Log.d(TAG, "saveWatchProgress: proceeding with save position=$position duration=$duration")

        val contentType = when (contentKind) {
            ContentKind.MOVIE -> "movie"
            ContentKind.SERIES -> "series"
            else -> { Log.w(TAG, "saveWatchProgress: unknown contentKind=$contentKind"); return }
        }

        val repo = watchProgressRepo
        if (repo == null) { Log.w(TAG, "saveWatchProgress: watchProgressRepo is null, skipping"); return }
        
        Log.d(TAG, "saveWatchProgress: launching async save for contentId='$contentId' position=$position")
        
        // Construir el item de progreso localmente para actualización inmediata de UI
        val progressItem = WatchProgressDto(
            contentId = contentId,
            contentType = contentType,
            positionMs = position,
            durationMs = duration,
            normalizedTitle = overlayTitle.trim(),
            title = overlayTitle,
            imageUrl = overlayLogoUrl,
            seriesName = currentSeriesEpisode?.seriesName,
            seasonNumber = currentSeriesEpisode?.seasonNumber,
            episodeNumber = currentSeriesEpisode?.episodeNumber,
            lastWatchedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date()),
            isWatched = false,
        )
        
        // Actualizar UI inmediatamente (callback asíncrono para no bloquear el player)
        Log.d(TAG, "saveWatchProgress: calling onProgressSaved callback, onProgressSaved=${onProgressSaved != null}")
        try {
            onProgressSaved?.invoke(progressItem)
            Log.d(TAG, "saveWatchProgress: onProgressSaved callback returned successfully")
        } catch (e: Exception) {
            Log.e(TAG, "saveWatchProgress: onProgressSaved callback threw exception", e)
        }
        
        // Guardar en backend (asíncrono)
        Log.d(TAG, "saveWatchProgress: launching lifecycleScope coroutine...")
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d(TAG, "saveWatchProgress: coroutine STARTED for contentId='$contentId'")
            try {
                repo.saveProgress(
                    contentId = contentId,
                    contentType = contentType,
                    positionMs = position,
                    durationMs = duration,
                    title = overlayTitle,
                    imageUrl = overlayLogoUrl,
                    seriesName = currentSeriesEpisode?.seriesName,
                    seasonNumber = currentSeriesEpisode?.seasonNumber,
                    episodeNumber = currentSeriesEpisode?.episodeNumber,
                )
                Log.d(TAG, "saveWatchProgress: coroutine COMPLETED successfully for contentId='$contentId'")
                // Verificar que el backend realmente guardó el item
                val verifyItem = repo.getProgress(contentId)
                if (verifyItem != null) {
                    Log.d(TAG, "saveWatchProgress: VERIFY OK - backend returned item contentId=${verifyItem.contentId} position=${verifyItem.positionMs}")
                } else {
                    Log.w(TAG, "saveWatchProgress: VERIFY FAIL - backend returned null for contentId='$contentId' after successful PUT")
                }
            } catch (e: Exception) {
                Log.e(TAG, "saveWatchProgress: coroutine FAILED for contentId='$contentId'", e)
            }
        }

        checkAndMarkWatched()
    }

    private fun fetchIntroDbSegments() {
        val episode = currentEpisode ?: return
        val imdbId = episode.imdbId ?: return
        val season = episode.seasonNumber ?: return
        val ep = episode.episodeNumber ?: return
        val repo = introDbRepository ?: return
        segmentButtonsHidden = false
        currentSegments = null

        lifecycleScope.launch(Dispatchers.IO) {
            val segments = repo.getSegments(imdbId, season, ep)
            withContext(Dispatchers.Main) {
                currentSegments = segments
                Log.d(TAG, "IntroDB segments loaded: ${segments != null}")
            }
        }
    }

    private fun bindSkipButtons() {
        val skipIntroBtn = playerView.findViewById<TextView>(R.id.skip_intro)
        val skipRecapBtn = playerView.findViewById<TextView>(R.id.skip_recap)
        val skipCreditsBtn = playerView.findViewById<TextView>(R.id.skip_credits)

        skipIntroBtn?.setOnClickListener {
            player?.let { p ->
                val endMs = currentSegments?.intro?.endMs ?: return@setOnClickListener
                segmentButtonsHidden = true
                p.seekTo(endMs)
            }
        }

        skipRecapBtn?.setOnClickListener {
            player?.let { p ->
                val endMs = currentSegments?.recap?.endMs ?: return@setOnClickListener
                segmentButtonsHidden = true
                p.seekTo(endMs)
            }
        }

        skipCreditsBtn?.setOnClickListener {
            player?.let { p ->
                val endMs = currentSegments?.outro?.endMs ?: return@setOnClickListener
                segmentButtonsHidden = true
                p.seekTo(endMs)
            }
        }
    }

    private fun updateSkipButtons() {
        if (segmentButtonsHidden) return
        val segments = currentSegments ?: return
        val exoPlayer = player ?: return
        val position = exoPlayer.currentPosition
        val isControllerVisible = playerView.isControllerFullyVisible

        val overlay = playerView.findViewById<View>(R.id.skip_overlay) ?: return
        val skipIntroBtn = playerView.findViewById<TextView>(R.id.skip_intro)
        val skipRecapBtn = playerView.findViewById<TextView>(R.id.skip_recap)
        val skipCreditsBtn = playerView.findViewById<TextView>(R.id.skip_credits)

        val bufferMs = 3000L
        var anyVisible = false

        segments.intro?.let {
            val endMs = it.endMs ?: return@let
            if (position < endMs + bufferMs) {
                skipIntroBtn?.visibility = View.VISIBLE
                anyVisible = true
            } else {
                skipIntroBtn?.visibility = View.GONE
            }
        }

        segments.recap?.let {
            val endMs = it.endMs ?: return@let
            if (position < endMs + bufferMs) {
                skipRecapBtn?.visibility = View.VISIBLE
                anyVisible = true
            } else {
                skipRecapBtn?.visibility = View.GONE
            }
        }

        segments.outro?.let {
            val startMs = it.startMs ?: return@let
            if (position >= startMs) {
                skipCreditsBtn?.visibility = View.VISIBLE
                anyVisible = true
            } else {
                skipCreditsBtn?.visibility = View.GONE
            }
        }

        if (isControllerVisible) {
            overlay.visibility = View.VISIBLE
        } else {
            overlay.visibility = if (anyVisible) View.VISIBLE else View.GONE
        }
    }

    private fun checkAndMarkWatched() {
        if (watchedMarked || contentId.isBlank() || !isVodMode) return
        val exoPlayer = player ?: return
        val position = exoPlayer.currentPosition
        val duration = exoPlayer.duration
        if (duration <= 0 || position <= 0) return
        if (position < duration * 90 / 100) return

        watchedMarked = true
        val repo = watchProgressRepo ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            when (contentKind) {
                ContentKind.MOVIE -> {
                    repo.markAsWatched(contentId)
                }
                ContentKind.SERIES -> {
                    repo.markAsWatched(contentId)
                }
                else -> Unit
            }
        }
    }

    private fun restoreWatchProgress() {
        if (contentId.isBlank() || !isVodMode) return
        val repo = watchProgressRepo ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val progress = repo.getProgress(contentId)
                if (progress != null && progress.shouldRestoreProgress){
                    withContext(Dispatchers.Main) {
                        player?.seekTo(progress.positionMs ?: 0L)
                        Log.d(TAG, "Restored progress to ${progress.positionMs}ms for $contentId")
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Could not restore progress: ${e.message}")
            }
        }
    }

    private fun updateTrackButtonStates() {
        if (!isVodMode) return

        val exoPlayer = player ?: return

        val hasSubtitleTracks = exoPlayer.currentTracks.groups.any { group ->
            group.type == C.TRACK_TYPE_TEXT && group.length > 0
        }

        val subtitleButton = playerView.findViewById<ImageButton>(R.id.vod_btn_subtitles)

        subtitleButton?.isEnabled = hasSubtitleTracks
        subtitleButton?.alpha = if (hasSubtitleTracks) 1.0f else 0.4f
    }

    private fun seekRelative(deltaMs: Long) {
        val exoPlayer = player ?: run {
            Log.w(TAG, "seekRelative: player is null, aborting")
            return
        }
        if (isReleasing) {
            Log.w(TAG, "seekRelative: isReleasing=true, aborting")
            return
        }
        val duration = exoPlayer.duration
        val position = exoPlayer.currentPosition
        val state = exoPlayer.playbackState
        Log.v(TAG, "seekRelative: delta=$deltaMs position=$position duration=$duration state=$state isReleasing=$isReleasing")
        if (duration == C.TIME_UNSET || duration <= 0) {
            Log.w(TAG, "seekRelative: duration invalid ($duration), aborting")
            return
        }
        if (exoPlayer.playbackState != Player.STATE_READY &&
            exoPlayer.playbackState != Player.STATE_BUFFERING) {
            Log.w(TAG, "seekRelative: bad playback state ($state), aborting")
            return
        }
        val target = (position + deltaMs).coerceIn(0, duration)
        Log.v(TAG, "seekRelative: seeking to $target")
        exoPlayer.seekTo(target)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Unified stream selector (audio + quality)
    // ──────────────────────────────────────────────────────────────────────

    private fun showUnifiedSelector() {
        val ctx = context ?: return
        val options = unifiedStreamOptions
        if (options.isEmpty()) return

        val labels = options.map { it.displayLabel }.toTypedArray()
        val currentUrl = streamUrl
        val currentIndex = options.indexOfFirst { it.url == currentUrl }.coerceAtLeast(0)

        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.vod_stream)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                if (which != currentIndex) {
                    onSelectUnifiedOption?.invoke(which)
                }
                dialog.dismiss()
            }
            .setOnDismissListener { player?.play() }
            .show()
    }

    private fun updateDisplayedMetadata(title: String, meta: String) {
        if (isVodMode) {
            playerView.findViewById<TextView>(R.id.vod_title)?.text = title
            playerView.findViewById<TextView>(R.id.vod_subtitle)?.text = meta
            return
        }

        if (::overlayTitleView.isInitialized) overlayTitleView.text = title
        if (::overlayMetaView.isInitialized) overlayMetaView.text = meta
    }

    private fun showSubtitleSelector() {
        val exoPlayer = player ?: return
        val ctx = context ?: return

        val textGroups = exoPlayer.currentTracks.groups.filter { group ->
            group.type == C.TRACK_TYPE_TEXT
        }

        if (textGroups.isEmpty()) {
            Toast.makeText(ctx, R.string.vod_no_subtitles_available, Toast.LENGTH_SHORT).show()
            player?.play()
            return
        }

        data class TrackChoice(
            val label: String,
            val groupIndex: Int,
            val trackIndex: Int,
            val isSelected: Boolean,
        )

        val choices = mutableListOf<TrackChoice>()

        val allDeselected = textGroups.none { group ->
            (0 until group.length).any { group.isTrackSelected(it) }
        }
        choices.add(TrackChoice(getString(R.string.vod_subtitle_off), -1, -1, allDeselected))

        textGroups.forEachIndexed { groupIdx, group ->
            for (trackIdx in 0 until group.length) {
                val format = group.getTrackFormat(trackIdx)
                val lang = normalizeLanguageCode(format.language)
                val trackLabel = format.label ?: languageDisplayLabel(lang)
                val selected = group.isTrackSelected(trackIdx)
                choices.add(TrackChoice(trackLabel, groupIdx, trackIdx, selected))
            }
        }

        val labels = choices.map { it.label }.toTypedArray()
        val checkedIndex = choices.indexOfFirst { it.isSelected }.coerceAtLeast(0)

        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.vod_subtitle_dialog_title)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                val chosen = choices[which]
                applySubtitleSelection(exoPlayer, textGroups, chosen.groupIndex, chosen.trackIndex)
                dialog.dismiss()
            }
            .setOnDismissListener { player?.play() }
            .show()
    }

    private fun applySubtitleSelection(
        exoPlayer: ExoPlayer,
        textGroups: List<Tracks.Group>,
        groupIndex: Int,
        trackIndex: Int,
    ) {
        val paramsBuilder = exoPlayer.trackSelectionParameters.buildUpon()
        if (groupIndex < 0) {
            paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            val group = textGroups[groupIndex]
            paramsBuilder.setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, trackIndex),
            )
        }
        exoPlayer.trackSelectionParameters = paramsBuilder.build()
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Option indicator (live / events)
    // ──────────────────────────────────────────────────────────────────────

    private fun bindOptionIndicator() {
        val total = streamOptionLabels.size
        if (total <= 1) {
            optionIndicatorView?.visibility = View.GONE
            return
        }
        optionIndicatorView?.visibility = View.VISIBLE
        updateOptionIndicator()
    }

    private fun updateOptionIndicator() {
        val total = streamOptionLabels.size
        if (total <= 1) return
        optionIndicatorView?.text = getString(R.string.option_counter, liveOptionIndex + 1, total)
        optionIndicatorView?.visibility = View.VISIBLE

        if (optionsListLayout?.visibility == View.VISIBLE) {
            showOptionsList()
        }
    }

    private fun showOptionsList() {
        val layout = optionsListLayout ?: return
        val labels = streamOptionLabels
        if (labels.size <= 1) return

        layout.removeAllViews()
        layout.visibility = View.VISIBLE

        val ctx = requireContext()
        labels.forEachIndexed { index, label ->
            val tv = TextView(ctx).apply {
                text = if (index == liveOptionIndex) "▸ $label" else "  $label"
                textSize = 14f
                setTextColor(
                    if (index == liveOptionIndex) 0xFF4FC3F7.toInt() else 0xCCFFFFFF.toInt()
                )
                setPadding(0, 4, 0, 4)
            }
            layout.addView(tv)
        }

        handler.removeCallbacks(hideOptionsListRunnable)
        handler.postDelayed(hideOptionsListRunnable, OVERLAY_DURATION_MS)
    }

    private val hideOptionsListRunnable = Runnable {
        optionsListLayout?.visibility = View.GONE
    }

    private fun bindOverlay() {
        updateOverlay(overlayNumber, overlayTitle, overlayMeta)
        btnChannelLabel?.text = overlayNumber

        if (overlayLogoUrl.isNotBlank()) {
            channelLogoView?.let { logoView ->
                Glide.with(this)
                    .load(overlayLogoUrl)
                    .centerCrop()
                    .into(logoView)
            }
            channelLogoView?.visibility = View.VISIBLE
        } else {
            channelLogoView?.visibility = View.GONE
        }

        showOverlayTemporarily()
    }

    private fun updateOverlay(number: String, title: String, meta: String) {
        overlayNumberView.text = number
        overlayTitleView.text = title
        overlayMetaView.text = meta
    }

    private fun showOverlayTemporarily() {
        if (!::overlayView.isInitialized) return
        overlayView.visibility = View.VISIBLE
        if (::bottomPanelView.isInitialized) {
            bottomPanelView.visibility = View.VISIBLE
        }
        handler.removeCallbacks(hideOverlayRunnable)
        if (!isMenuFocused()) {
            handler.postDelayed(hideOverlayRunnable, 3000)
            Log.d("OverlayDebug", "Timer started, hide in 3s")
        }
    }

    private fun isMenuFocused(): Boolean {
        val g = btnGuide?.hasFocus() == true
        val f = btnFavorites?.hasFocus() == true
        val c = btnChannel?.hasFocus() == true
        Log.v(TAG, "FAV_MENU_FOCUS: guide=$g fav=$f ch=$c")
        return g || f || c
    }

    fun isOverlayMenuFocused(): Boolean =
        isMenuFocused() || (::overlayView.isInitialized && overlayView.isVisible)

    private fun hideOverlay() {
        handler.removeCallbacks(hideOverlayRunnable)
        hideOverlayRunnable.run()
    }

    fun hideOverlayMenu() {
        handler.removeCallbacks(hideOverlayRunnable)
        if (::overlayView.isInitialized) overlayView.visibility = View.GONE
        if (::bottomPanelView.isInitialized) bottomPanelView.visibility = View.GONE
    }

    /**
     * Llamado por MainActivity cuando el usuario pulsa BACK con el player visible.
     * En VOD: si el controlador está visible lo oculta y devuelve true (consumido).
     * Si ya está oculto devuelve false para que MainActivity cierre el player.
     * En live: devuelve false siempre.
     */
    fun handleBackPress(): Boolean {
        if (!isVodMode) return false
        return if (playerView.isControllerFullyVisible) {
            Log.d(TAG, "handleBackPress: controller visible → hiding")
            playerView.hideController()
            true
        } else {
            Log.d(TAG, "handleBackPress: controller hidden → letting MainActivity close player")
            false
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  MediaItem creation
    // ──────────────────────────────────────────────────────────────────────

    private fun handleBufferingTimeout() {
        if (isReleasing || isVodMode || isRetrying) return
        val elapsed = System.currentTimeMillis() - bufferingSinceMs
        val stillBuffering = player?.playbackState == Player.STATE_BUFFERING
        if (stillBuffering) {
            Log.w(TAG, "Live buffering timeout after ${elapsed}ms. Forcing restart.")
            Toast.makeText(context, "Timeout buffering ${elapsed/1000}s — reiniciando", Toast.LENGTH_SHORT).show()
            handlePlaybackError(null)
        }
    }

    private fun checkPositionStuck() {
        if (isReleasing || isVodMode || isRetrying) return
        val exoPlayer = player ?: return
        if (exoPlayer.playbackState != Player.STATE_READY || !exoPlayer.isPlaying) {
            lastKnownPositionMs = Long.MIN_VALUE
            stuckPositionCount = 0
            handler.postDelayed(positionWatchdog, POSITION_CHECK_INTERVAL_MS)
            return
        }
        val currentPos = exoPlayer.currentPosition
        val delta = currentPos - lastKnownPositionMs
        val isFirstCheck = lastKnownPositionMs == Long.MIN_VALUE
        if (isFirstCheck) {
            lastKnownPositionMs = currentPos
            handler.postDelayed(positionWatchdog, POSITION_CHECK_INTERVAL_MS)
            return
        }
        if (delta > 0) {
            stuckPositionCount = 0
            lastKnownPositionMs = currentPos
        } else {
            stuckPositionCount++
            if (stuckPositionCount >= MAX_STUCK_CHECKS) {
                stuckPositionCount = 0
                lastKnownPositionMs = Long.MIN_VALUE
                handlePlaybackError(null)
                return
            }
        }
        handler.postDelayed(positionWatchdog, POSITION_CHECK_INTERVAL_MS)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Playback error handling
    // ──────────────────────────────────────────────────────────────────────

    private fun handlePlaybackError(error: PlaybackException? = null) {
        if (isReleasing) return

        val categorizedError = categorizePlaybackError(
            error = error,
            isVodMode = isVodMode,
            hasQualityOptions = streamOptionLabels.size > 1,
            hasNextChannel = !isVodMode,
        )

        val errorMessage = error?.toString() ?: ""
        val isCodecIncompatible = isFatalPlaybackErrorForDevice(errorMessage)

        if (isCodecIncompatible) {
            Log.w(TAG, "Error de codec incompatible detectado: $errorMessage")
            if (isVodMode && unifiedStreamOptions.size > 1 && onSelectUnifiedOption != null) {
                val nextIndex = currentOptionIndex + 1
                if (nextIndex < unifiedStreamOptions.size) {
                    Log.d(TAG, "Auto-fallback de calidad: ${streamOptionLabels.getOrNull(currentOptionIndex)} → ${streamOptionLabels.getOrNull(nextIndex)}")
                    onSelectUnifiedOption?.invoke(nextIndex)
                    return
                }
                Log.w(TAG, "Auto-fallback: todas las opciones de calidad agotadas")
                showErrorOverlay(categorizedError, autoClose = false)
                handler.postDelayed({ showUnifiedSelector() }, 500)
                return
            }
            Toast.makeText(context, R.string.codec_unsupported_device, Toast.LENGTH_LONG).show()
            showErrorOverlay(categorizedError, autoClose = false)
            releasePlayer()
            runCatching {
                parentFragmentManager.beginTransaction()
                    .remove(this)
                    .commitAllowingStateLoss()
            }.onFailure {
                Log.e(TAG, "No se pudo cerrar el reproductor tras error fatal", it)
            }
            return
        }

        if (retryCount < MAX_RETRIES) {
            retryCount += 1
            isRetrying = true
            showErrorOverlay(categorizedError)
            val delay = if (!isVodMode) {
                when (retryCount) {
                    1 -> 0L
                    2 -> 500L
                    3 -> 1_000L
                    else -> RETRY_DELAY_MS
                }
            } else {
                RETRY_DELAY_MS
            }
            handler.postDelayed({
                if (player != null && !isReleasing) {
                    try {
                        player?.let { exoPlayer ->
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                            exoPlayer.setMediaItem(createMediaItem(streamUrl))
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                    } catch (exception: Exception) {
                        Log.e(TAG, "Error al reintentar reproduccion", exception)
                        isRetrying = false
                    }
                }
            }, delay)
        } else {
            isRetrying = false
            showErrorOverlay(categorizedError)
            handler.postDelayed({
                if (!isReleasing) {
                    retryCount = 0
                    releasePlayer()
                    initializePlayer()
                }
            }, FORCE_RESTART_DELAY_MS)
        }
    }

    private fun showErrorOverlay(error: PlaybackError, autoClose: Boolean = true) {
        errorState = error
        val composeView = errorComposeView ?: return

        val autoActionCallback: (() -> Unit)? = when {
            !autoClose -> null
            isEventMode -> {
                val hasMoreOptions = liveOptionIndex < streamOptionLabels.size - 1
                if (hasMoreOptions) {
                    { onNavigateOption?.invoke(1) }
                } else {
                    { closeFromHost() }
                }
            }
            else -> {
                { closeFromHost() }
            }
        }

        composeView.setContent {
            errorState?.let { currentError ->
                PlayerErrorOverlay(
                    error = currentError,
                    isRetrying = isRetrying,
                    onAutoAction = autoActionCallback,
                )
            }
        }
    }

    private fun hideErrorOverlay() {
        errorState = null
        isRetrying = false
        val composeView = errorComposeView ?: return
        composeView.setContent { }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Key handling
    // ──────────────────────────────────────────────────────────────────────

    fun dispatchKeyToPlayer(event: KeyEvent): Boolean = handleKeyPress(event)

    private fun handleKeyPress(event: KeyEvent): Boolean =
        if (isVodMode) handleVodKeyPress(event) else handleLiveKeyPress(event)

    private fun handleVodKeyPress(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val keyCode = event.keyCode

        val focusedView = playerView.findFocus()
        val focusedCustomButton = getFocusedVodButton()
        val focusOnPlayPause = isFocusOnPlayPauseButton()
        val focusOnProgressBar = isFocusOnProgressBar()

        Log.v(TAG, "VOD_KEY: keyCode=$keyCode focusedView=${focusedView?.let { describeView(it) }} " +
                "customBtn=${focusedCustomButton?.let { describeView(it) }} " +
                "playPause=$focusOnPlayPause progressBar=$focusOnProgressBar")

        return when (keyCode) {

            KeyEvent.KEYCODE_DPAD_LEFT -> when {
                focusedCustomButton != null -> {
                    Log.v(TAG, "VOD_LEFT: focus on custom button → letting system handle focus")
                    false
                }
                focusOnProgressBar -> {
                    Log.v(TAG, "VOD_LEFT: focus on progress bar → seeking backward")
                    seekRelative(getSeekIncrement(-1))
                    playerView.showController()
                    true
                }
                else -> {
                    Log.v(TAG, "VOD_LEFT: seeking backward")
                    seekRelative(getSeekIncrement(-1))
                    playerView.showController()
                    true
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> when {
                focusedCustomButton != null -> {
                    Log.v(TAG, "VOD_RIGHT: focus on custom button → letting system handle focus")
                    false
                }
                focusOnPlayPause -> {
                    Log.v(TAG, "VOD_RIGHT: focus on play/pause → moving to first custom button")
                    moveFocusToFirstCustomButton()
                    true
                }
                focusOnProgressBar -> {
                    Log.v(TAG, "VOD_RIGHT: focus on progress bar → seeking forward")
                    seekRelative(getSeekIncrement(1))
                    playerView.showController()
                    true
                }
                else -> {
                    Log.v(TAG, "VOD_RIGHT: no special focus → seeking forward")
                    seekRelative(getSeekIncrement(1))
                    playerView.showController()
                    true
                }
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                val focused = getFocusedVodButton()
                Log.v(TAG, "VOD_CENTER: focusedCustomButton=${focused?.let { describeView(it) }}")
                when {
                    focused != null -> {
                        Log.v(TAG, "VOD_CENTER: performing click on ${describeView(focused)}")
                        focused.performClick()
                        true
                    }
                    else -> {
                        Log.v(TAG, "VOD_CENTER: toggling play/pause")
                        player?.let { if (it.isPlaying) it.pause() else it.play() }
                        playerView.showController()
                        true
                    }
                }
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                playerView.showController()
                when {
                    isFocusOnPlayPauseButton() || getFocusedVodButton() != null -> {
                        Log.v(TAG, "VOD_UP: buttons → progress bar")
                        val progressView = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
                        progressView?.requestFocus()
                    }
                    else -> {
                        Log.v(TAG, "VOD_UP: already on top row")
                    }
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                playerView.showController()
                when {
                    isFocusOnProgressBar() -> {
                        Log.v(TAG, "VOD_DOWN: progress bar → play/pause")
                        moveToPlayPauseButton()
                    }
                    else -> {
                        Log.v(TAG, "VOD_DOWN: already on bottom row")
                    }
                }
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player?.play(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.pause(); true }
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                seekRelative(getSeekIncrement(-1))
                playerView.showController()
                true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                seekRelative(getSeekIncrement(1))
                playerView.showController()
                true
            }

            // BACK lo gestiona MainActivity via handleBackPress()
            KeyEvent.KEYCODE_BACK -> false

            else -> {
                playerView.showController()
                false
            }
        }
    }

    private fun describeView(view: View): String {
        return try {
            val resName = view.resources.getResourceEntryName(view.id)
            "$resName(${view.javaClass.simpleName})"
        } catch (e: Exception) {
            "id=${view.id}(${view.javaClass.simpleName})"
        }
    }

    private fun getFocusedVodButton(): View? {
        val customButtonIds = listOf(
            R.id.vod_btn_stream,
            R.id.vod_btn_subtitles,
            R.id.vod_btn_next,
            R.id.vod_btn_prev,
        )
        return customButtonIds
            .mapNotNull { id -> playerView.findViewById<View>(id) }
            .firstOrNull { it.hasFocus() }
    }

    private fun isFocusOnPlayPauseButton(): Boolean {
        val playPauseView = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
        val playView = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play)
        val pauseView = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_pause)
        val result = playPauseView?.hasFocus() == true ||
                playView?.hasFocus() == true ||
                pauseView?.hasFocus() == true
        Log.v(TAG, "isFocusOnPlayPauseButton: playPause=${playPauseView?.hasFocus()} " +
                "play=${playView?.hasFocus()} pause=${pauseView?.hasFocus()} → $result")
        return result
    }

    private fun isFocusOnProgressBar(): Boolean {
        val progressView = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
        val result = progressView?.hasFocus() == true
        Log.v(TAG, "isFocusOnProgressBar: ${progressView?.hasFocus()} → $result")
        return result
    }

    private fun moveToPlayPauseButton() {
        val playPauseView = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
            ?: playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play)
            ?: playerView.findViewById<View>(androidx.media3.ui.R.id.exo_pause)
        Log.v(TAG, "moveToPlayPauseButton: target=${playPauseView?.let { describeView(it) }}")
        playPauseView?.requestFocus()
        playerView.showController()
    }

    private fun moveFocusToFirstCustomButton() {
        val customButtonIds = listOf(
            R.id.vod_btn_prev,
            R.id.vod_btn_next,
            R.id.vod_btn_stream,
            R.id.vod_btn_subtitles,
        )
        val target = customButtonIds
            .mapNotNull { id -> playerView.findViewById<View>(id) }
            .firstOrNull { it.isVisible && it.isEnabled }
        Log.v(TAG, "moveFocusToFirstCustomButton: target=${target?.let { describeView(it) }}")
        target?.requestFocus()
        playerView.showController()
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Seek progresivo
    // ──────────────────────────────────────────────────────────────────────

    private var seekPressCount: Int = 0
    private var seekPressDirection: Int = 0
    private var lastSeekPressTime: Long = 0L
    private val SEEK_RAPID_THRESHOLD_MS = 500L

    private fun getSeekIncrement(direction: Int): Long {
        val now = System.currentTimeMillis()

        if (seekPressDirection != direction || (now - lastSeekPressTime) > SEEK_RAPID_THRESHOLD_MS) {
            seekPressCount = 0
            seekPressDirection = direction
        }

        seekPressCount++
        lastSeekPressTime = now

        val base = when {
            seekPressCount <= 30 -> 10_000L
            seekPressCount <= 40 -> 30_000L
            else -> 45_000L
        }
        return base * direction
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Live key handling
    // ──────────────────────────────────────────────────────────────────────

    private fun handleLiveKeyPress(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (contentKind == ContentKind.CHANNEL) {
            mapDigit(keyCode)?.let { digit ->
                appendDigit(digit)
                return true
            }
        }

        return when (keyCode) {

            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isMenuFocused()) return true
                if (::overlayView.isInitialized && overlayView.isVisible) {
                    handler.removeCallbacks(hideOverlayRunnable)
                    val focusResult = btnGuide?.requestFocus() ?: false
                    Log.v(TAG, "FAV_UP: btnGuide.requestFocus()=$focusResult")
                    return true
                }
                val newIndex = liveOptionIndex - 1
                if (streamOptionLabels.size > 1 && newIndex >= 0) {
                    liveOptionIndex = newIndex
                    updateOptionIndicator()
                    showOptionsList()
                    showOverlayTemporarily()
                    onNavigateOption?.invoke(-1)
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isMenuFocused()) return true
                if (::overlayView.isInitialized && overlayView.isVisible) {
                    handler.removeCallbacks(hideOverlayRunnable)
                    val focusResult = btnGuide?.requestFocus() ?: false
                    Log.v(TAG, "FAV_DOWN: btnGuide.requestFocus()=$focusResult")
                    return true
                }
                val newIndex = liveOptionIndex + 1
                if (streamOptionLabels.size > 1 && newIndex < streamOptionLabels.size) {
                    liveOptionIndex = newIndex
                    updateOptionIndicator()
                    showOptionsList()
                    showOverlayTemporarily()
                    onNavigateOption?.invoke(1)
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isMenuFocused()) return false
                showOverlayTemporarily()
                onNavigateChannel?.invoke(1)
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isMenuFocused()) return false
                showOverlayTemporarily()
                onNavigateChannel?.invoke(-1)
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                val focusedButton = when {
                    btnGuide?.hasFocus() == true -> btnGuide
                    btnFavorites?.hasFocus() == true -> btnFavorites
                    btnChannel?.hasFocus() == true -> btnChannel
                    else -> null
                }
                Log.v(TAG, "FAV_CENTER: focusedButton=${focusedButton?.id} guide=${btnGuide?.hasFocus()} fav=${btnFavorites?.hasFocus()} ch=${btnChannel?.hasFocus()}")
                if (focusedButton != null) {
                    focusedButton.performClick()
                    return true
                }
                showOverlayTemporarily()
                true
            }

            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BOOKMARK -> {
                val nowFavorite = onToggleFavorite?.invoke() ?: false
                isFavoriteState = nowFavorite
                updateFavoriteIcon()
                updateOverlay(
                    overlayNumber,
                    overlayTitle,
                    if (nowFavorite) getString(R.string.live_favorite_saved)
                    else getString(R.string.live_favorite_removed),
                )
                showOverlayTemporarily()
                true
            }

            KeyEvent.KEYCODE_GUIDE -> {
                val opened = onOpenFavorites?.invoke() ?: false
                if (!opened) {
                    updateOverlay(overlayNumber, overlayTitle, getString(R.string.live_no_favorites))
                    showOverlayTemporarily()
                }
                true
            }

            KeyEvent.KEYCODE_INFO -> {
                val opened = onOpenRecents?.invoke() ?: false
                if (!opened) {
                    updateOverlay(overlayNumber, overlayTitle, getString(R.string.live_no_recents))
                    showOverlayTemporarily()
                }
                true
            }

            KeyEvent.KEYCODE_BACK -> false

            else -> false
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Digit buffer (channels only)
    // ──────────────────────────────────────────────────────────────────────

    private fun appendDigit(digit: Int) {
        if (digitBuffer.length >= 4) digitBuffer.clear()
        digitBuffer.append(digit)
        updateOverlay(
            "CH ${digitBuffer}",
            getString(R.string.live_direct_tuning),
            getString(R.string.live_direct_hint),
        )
        showOverlayTemporarily()
        handler.removeCallbacks(commitDigitsRunnable)
        handler.postDelayed(commitDigitsRunnable, DIRECT_ZAP_DELAY_MS)
    }

    private val commitDigitsRunnable = Runnable {
        val value = digitBuffer.toString().toIntOrNull()
        digitBuffer.clear()
        if (value == null || value <= 0) return@Runnable
        val changed = onDirectChannelNumber?.invoke(value) ?: false
        if (!changed) {
            updateOverlay(
                "CH $value",
                getString(R.string.live_channel_not_found),
                getString(R.string.live_channel_not_found_hint),
            )
            showOverlayTemporarily()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Lifecycle
    // ──────────────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer(closeUi = false, notifyHost = false)
    }

    fun closeFromHost() {
        Log.d(TAG, "closeFromHost called")
        closedByHost = true
        playerClosed = true
        releasePlayer()
        Log.d(TAG, "closeFromHost: releasePlayer returned")
    }

    private fun releasePlayer(closeUi: Boolean = true, notifyHost: Boolean = true) {
        Log.d(TAG, "releasePlayer: isReleasing=$isReleasing, closedByHost=$closedByHost, closeUi=$closeUi, notifyHost=$notifyHost, isVodMode=$isVodMode, contentId='$contentId', player=${player != null}")

        val shouldSave = isVodMode && contentId.isNotBlank()
        Log.d(TAG, "releasePlayer: shouldSave=$shouldSave (isVodMode=$isVodMode, contentId='$contentId')")
        if (shouldSave) {
            Log.d(TAG, "releasePlayer: calling saveWatchProgress(forceSave=true)...")
            saveWatchProgress(forceSave = true)
            Log.d(TAG, "releasePlayer: saveWatchProgress returned")
        }

        isReleasing = true
        isPlayerInitialized = false
        if (closeUi) playerClosed = true
        handler.removeCallbacksAndMessages(null)

        if (::playerView.isInitialized) {
            playerView.player = null
        }

        player?.let { exoPlayer ->
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (exception: Exception) {
                Log.e(TAG, "Error al liberar player", exception)
            }
        }

        player = null
        retryCount = 0

        if (closeUi) {
            activity?.findViewById<FrameLayout>(R.id.player_container)?.visibility = View.GONE
        }

        if (notifyHost && !closedByHost) {
            Log.d(TAG, "Player closed without host, notifying via onPlayerClosed callback")
            onPlayerClosed?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        isReleasing = false
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Runnables & listeners
    // ──────────────────────────────────────────────────────────────────────

    private val hideOverlayRunnable = Runnable {
        Log.d("OverlayDebug", "FAV_HIDE: isMenuFocused=${isMenuFocused()}, willHide=${!isMenuFocused()}")
        if (isMenuFocused()) return@Runnable
        if (::overlayView.isInitialized) overlayView.visibility = View.GONE
        if (::bottomPanelView.isInitialized) bottomPanelView.visibility = View.GONE
    }

    private inner class PlayerListener : Player.Listener {
        private var progressRestored = false

        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName = playbackStateName(playbackState)
            Log.d(TAG, "onPlaybackStateChanged: $stateName | isPlaying=${player?.isPlaying} isVod=$isVodMode")
            when (playbackState) {
                Player.STATE_READY -> {
                    handler.removeCallbacks(bufferingWatchdog)
                    retryCount = 0
                    isPlayerInitialized = true
                    isRetrying = false
                    hideErrorOverlay()
                    updateTrackButtonStates()
                    if (isVodMode) {
                        playerView.requestFocus()
                        playerView.showController()
                        if (!progressRestored) {
                            progressRestored = true
                            restoreWatchProgress()
                        }
                    } else {
                        lastKnownPositionMs = Long.MIN_VALUE
                        stuckPositionCount = 0
                        handler.removeCallbacks(positionWatchdog)
                        handler.postDelayed(positionWatchdog, POSITION_CHECK_INTERVAL_MS)
                        showOverlayTemporarily()
                    }
                }
                Player.STATE_BUFFERING -> {
                    retryCount = 0
                    if (!isVodMode) {
                        handler.removeCallbacks(bufferingWatchdog)
                        handler.removeCallbacks(positionWatchdog)
                        lastKnownPositionMs = Long.MIN_VALUE
                        stuckPositionCount = 0
                        bufferingSinceMs = System.currentTimeMillis()
                        handler.postDelayed(bufferingWatchdog, BUFFERING_TIMEOUT_LIVE_MS)
                    }
                }
                else -> {
                    if (isVodMode) {
                        if (!advancedToNext && !isReleasing && !isRetrying &&
                            playbackState == Player.STATE_ENDED &&
                            contentKind == ContentKind.SERIES && onNextEpisode != null
                        ) {
                            advancedToNext = true
                            Log.d(TAG, "VOD STATE_ENDED: advancing to next episode")
                            saveWatchProgress(forceSave = true)
                            onNextEpisode?.invoke()
                        }
                    } else {
                        handler.removeCallbacks(positionWatchdog)
                        lastKnownPositionMs = Long.MIN_VALUE
                        stuckPositionCount = 0
                        if (!isReleasing && !isRetrying && playbackState == Player.STATE_ENDED) {
                            handler.postDelayed({
                                if (!isReleasing) handlePlaybackError(null)
                            }, 100L)
                        } else if (!isReleasing && !isRetrying && playbackState == Player.STATE_IDLE && player != null) {
                            handler.postDelayed({
                                if (!isReleasing) handlePlaybackError(null)
                            }, 200L)
                        }
                    }
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            Log.d(TAG, "onTracksChanged")
            updateTrackButtonStates()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "onPlayerError: ${error.message} errorCode=${error.errorCodeName}", error)
            if (!isVodMode) {
                handler.removeCallbacks(positionWatchdog)
                lastKnownPositionMs = Long.MIN_VALUE
                stuckPositionCount = 0
            }
            handlePlaybackError(error)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val stateName = playbackStateName(player?.playbackState ?: -1)
            Log.d(TAG, "onIsPlayingChanged: isPlaying=$isPlaying playbackState=$stateName isVod=$isVodMode")
            if (isPlaying && !isVodMode) {
                lastKnownPositionMs = Long.MIN_VALUE
                stuckPositionCount = 0
                handler.removeCallbacks(positionWatchdog)
                handler.postDelayed(positionWatchdog, POSITION_CHECK_INTERVAL_MS)
            }
            if (!isVodMode && !isPlaying && !isReleasing) {
                val state = player?.playbackState
                if (state == Player.STATE_BUFFERING) {
                    handler.postDelayed({
                        if (player != null && !isReleasing && player?.playbackState == Player.STATE_BUFFERING && !player!!.isPlaying) {
                            handlePlaybackError(null)
                        }
                    }, STALL_RECOVERY_MS)
                } else if (state == Player.STATE_READY) {
                    handler.postDelayed({
                        if (player != null && !isReleasing) player?.play()
                    }, 1_000)
                }
            }
        }
    }

    companion object {
        private const val TAG = "PlayerFragment"
        private const val MAX_RETRIES = 8
        private const val RETRY_DELAY_MS = 2_000L
        private const val FORCE_RESTART_DELAY_MS = 3_000L
        private const val BUFFERING_TIMEOUT_LIVE_MS = 20_000L
        private const val STALL_RECOVERY_MS = 8_000L
        private const val POSITION_CHECK_INTERVAL_MS = 5_000L
        private const val MAX_STUCK_CHECKS = 4
        private const val OVERLAY_DURATION_MS = 6_000L
        private const val DIRECT_ZAP_DELAY_MS = 1_500L
        private const val VOD_CONTROLLER_TIMEOUT_MS = 5_000
        private const val PROGRESS_SAVE_INTERVAL_MS = 30_000L
    }
}
