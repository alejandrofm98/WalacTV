package com.example.walactv

import android.app.AlertDialog
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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.media3.common.C
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
import androidx.media3.ui.PlayerView

@UnstableApi
class PlayerFragment(
    private val streamUrl: String,
    private val overlayNumber: String,
    private val overlayTitle: String,
    private val overlayMeta: String,
    private val contentKind: ContentKind,
    private val onNavigateChannel: (direction: Int) -> Unit,
    private val onNavigateOption: (direction: Int) -> Unit,
    private val onDirectChannelNumber: (Int) -> Boolean,
    private val onToggleFavorite: () -> Boolean,
    private val onOpenFavorites: () -> Boolean,
    private val onOpenRecents: () -> Boolean,
    private val onNextEpisode: (() -> Unit)? = null,
) : Fragment() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var overlayView: LinearLayout
    private lateinit var overlayNumberView: TextView
    private lateinit var overlayTitleView: TextView
    private lateinit var overlayMetaView: TextView
    private lateinit var bottomPanelView: LinearLayout
    private lateinit var bottomTitleView: TextView
    private lateinit var bottomMetaView: TextView
    private lateinit var bottomGuideView: TextView
    private lateinit var bottomHistoryView: TextView
    private lateinit var bottomActionOneView: TextView
    private lateinit var bottomActionTwoView: TextView
    private lateinit var bottomActionThreeView: TextView
    private var liveBtnAudio: ImageButton? = null
    private var liveBtnSubtitles: ImageButton? = null
    private val handler = Handler(Looper.getMainLooper())
    private val digitBuffer = StringBuilder()

    private var retryCount: Int = 0
    private var isPlayerInitialized: Boolean = false
    private var isReleasing: Boolean = false
    private lateinit var trackSelector: DefaultTrackSelector

    /** True when the content is a movie or series (VOD mode). */
    private val isVodMode: Boolean
        get() = contentKind == ContentKind.MOVIE || contentKind == ContentKind.SERIES

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.player_view, container, false)
        playerView = view.findViewById(R.id.playerView)

        if (isVodMode) {
            setupVodMode()
        } else {
            setupLiveMode(view)
        }

        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()

        return view
    }

    /** Configure the player view for VOD (movies/series) with Media3 controller. */
    private fun setupVodMode() {
        playerView.useController = true
        playerView.controllerShowTimeoutMs = VOD_CONTROLLER_TIMEOUT_MS
        playerView.controllerAutoShow = true

        // Disable default time display - we use custom TextViews with proper formatting
        playerView.setShowNextButton(false)
        playerView.setShowPreviousButton(false)

        // Hide live-TV overlays since VOD uses its own controller
        // (channel_overlay and player_bottom_panel are in player_view.xml but not needed here)
        (playerView.parent as? ViewGroup)?.let { parent ->
            parent.findViewById<View>(R.id.channel_overlay)?.visibility = View.GONE
            parent.findViewById<View>(R.id.player_bottom_panel)?.visibility = View.GONE
        }

        // Set title text inside the VOD controller layout
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                if (visibility == View.VISIBLE) {
                    playerView.findViewById<TextView>(R.id.vod_title)?.text = overlayTitle
                    playerView.findViewById<TextView>(R.id.vod_subtitle)?.text = overlayMeta
                    updateVodTimeDisplay()
                }
            },
        )
    }

    /** Format time as H:MM:SS or M:SS (Stremio-style). */
    private fun formatTime(ms: Long): String {
        if (ms <= 0 || ms == C.TIME_UNSET) return "0:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    /** Update the custom position/duration TextViews with formatted time. */
    private fun updateVodTimeDisplay() {
        val exoPlayer = player ?: return
        playerView.findViewById<TextView>(R.id.vod_position)?.text = formatTime(exoPlayer.currentPosition)
        playerView.findViewById<TextView>(R.id.vod_duration)?.text = formatTime(exoPlayer.duration)
    }

    /** Configure the player view for live TV with the existing custom overlay. */
    private fun setupLiveMode(view: View) {
        playerView.useController = false
        overlayView = view.findViewById(R.id.channel_overlay)
        overlayNumberView = view.findViewById(R.id.channel_number)
        overlayTitleView = view.findViewById(R.id.channel_title)
        overlayMetaView = view.findViewById(R.id.channel_meta)
        bottomPanelView = view.findViewById(R.id.player_bottom_panel)
        bottomTitleView = view.findViewById(R.id.bottom_now_title)
        bottomMetaView = view.findViewById(R.id.bottom_now_meta)
        bottomGuideView = view.findViewById(R.id.bottom_action_guide)
        bottomHistoryView = view.findViewById(R.id.bottom_action_history)
        bottomActionOneView = view.findViewById(R.id.bottom_action_one)
        bottomActionTwoView = view.findViewById(R.id.bottom_action_two)
        bottomActionThreeView = view.findViewById(R.id.bottom_action_three)
        liveBtnAudio = view.findViewById(R.id.live_btn_audio)
        liveBtnSubtitles = view.findViewById(R.id.live_btn_subtitles)
        bindLiveTrackButtons()
        bindOverlay()
    }

    /** Wire live TV audio/subtitle buttons. */
    private fun bindLiveTrackButtons() {
        liveBtnAudio?.setOnClickListener { showAudioSelector() }
        liveBtnSubtitles?.setOnClickListener { showSubtitleSelector() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleKeyPress(keyCode)
            } else {
                false
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isPlayerInitialized) {
            initializePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
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

        try {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(30_000)
                .setReadTimeoutMs(30_000)
                .setUserAgent("WalacTV/AndroidTV")
                .setDefaultRequestProperties(
                    mapOf(
                        "Accept" to "*/*",
                        "Accept-Encoding" to "gzip, deflate",
                        "Connection" to "keep-alive",
                    ),
                )

            val mediaSourceFactory = DefaultMediaSourceFactory(requireContext())
                .setDataSourceFactory(dataSourceFactory)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 15_000,
                    /* maxBufferMs = */ 120_000,
                    /* bufferForPlaybackMs = */ 2_500,
                    /* bufferForPlaybackAfterRebufferMs = */ 5_000,
                )
                .build()

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
                    exoPlayer.addListener(PlayerListener())
                    exoPlayer.setMediaItem(createMediaItem(streamUrl))
                    exoPlayer.prepare()
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
                handler.postDelayed(this, 1000)
            }
        }
    }

    /** Wire the custom buttons inside the VOD controller layout. */
    private fun bindVodControls() {
        playerView.findViewById<ImageButton>(R.id.vod_btn_audio)?.setOnClickListener {
            showAudioSelector()
        }

        playerView.findViewById<ImageButton>(R.id.vod_btn_subtitles)?.setOnClickListener {
            showSubtitleSelector()
        }

        // Show and wire the "next episode" button only for series with a callback
        val nextBtn = playerView.findViewById<ImageButton>(R.id.vod_btn_next)
        if (contentKind == ContentKind.SERIES && onNextEpisode != null) {
            nextBtn?.visibility = View.VISIBLE
            nextBtn?.setOnClickListener { onNextEpisode.invoke() }
        } else {
            nextBtn?.visibility = View.GONE
        }

        // Start periodic time display updates
        handler.removeCallbacks(timeUpdateRunnable)
        handler.post(timeUpdateRunnable)
    }

    /**
     * Update button enabled/disabled states based on available tracks.
     * Called when tracks are detected or change.
     */
    private fun updateTrackButtonStates() {
        val exoPlayer = player ?: return

        val hasAudioTracks = exoPlayer.currentTracks.groups.any { group ->
            group.type == C.TRACK_TYPE_AUDIO && group.length > 0
        }

        val hasSubtitleTracks = exoPlayer.currentTracks.groups.any { group ->
            group.type == C.TRACK_TYPE_TEXT && group.length > 0
        }

        val audioButton = if (isVodMode) {
            playerView.findViewById<ImageButton>(R.id.vod_btn_audio)
        } else {
            liveBtnAudio
        }

        val subtitleButton = if (isVodMode) {
            playerView.findViewById<ImageButton>(R.id.vod_btn_subtitles)
        } else {
            liveBtnSubtitles
        }

        audioButton?.isEnabled = hasAudioTracks
        audioButton?.alpha = if (hasAudioTracks) 1.0f else 0.4f

        subtitleButton?.isEnabled = hasSubtitleTracks
        subtitleButton?.alpha = if (hasSubtitleTracks) 1.0f else 0.4f
    }

    /** Seek the player forward or backward by [deltaMs] milliseconds. */
    private fun seekRelative(deltaMs: Long) {
        val exoPlayer = player ?: return
        if (isReleasing) return

        // Don't seek if the player isn't ready or duration is unknown.
        // C.TIME_UNSET is Long.MIN_VALUE+1; coercing against it produces
        // garbage values that crash Util.getStringForTime() on the
        // MediaCodec_loop thread.
        val duration = exoPlayer.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        if (exoPlayer.playbackState != Player.STATE_READY &&
            exoPlayer.playbackState != Player.STATE_BUFFERING) return

        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0, duration)
        exoPlayer.seekTo(target)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Audio track selection
    // ──────────────────────────────────────────────────────────────────────

    /** Show a dialog listing available audio tracks for the current media. */
    private fun showAudioSelector() {
        val exoPlayer = player ?: return
        val ctx = context ?: return

        val audioGroups = exoPlayer.currentTracks.groups.filter { group ->
            group.type == C.TRACK_TYPE_AUDIO
        }

        if (audioGroups.isEmpty()) {
            Toast.makeText(ctx, R.string.vod_no_audio_available, Toast.LENGTH_SHORT).show()
            return
        }

        data class AudioTrackChoice(
            val label: String,
            val groupIndex: Int,
            val trackIndex: Int,
            val isSelected: Boolean,
        )

        val choices = mutableListOf<AudioTrackChoice>()

        audioGroups.forEachIndexed { groupIdx, group ->
            for (trackIdx in 0 until group.length) {
                val format = group.getTrackFormat(trackIdx)
                val lang = format.language?.uppercase() ?: "???"
                val channelCount = format.channelCount
                val label = if (channelCount > 0) {
                    "$lang ($channelCount ch)"
                } else {
                    format.label ?: lang
                }
                val selected = group.isTrackSelected(trackIdx)
                choices.add(AudioTrackChoice(label, groupIdx, trackIdx, selected))
            }
        }

        val labels = choices.map { it.label }.toTypedArray()
        val checkedIndex = choices.indexOfFirst { it.isSelected }.coerceAtLeast(0)

        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.vod_audio_dialog_title)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                val chosen = choices[which]
                applyAudioSelection(exoPlayer, audioGroups, chosen.groupIndex, chosen.trackIndex)
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Apply the user's audio track choice.
     */
    private fun applyAudioSelection(
        exoPlayer: ExoPlayer,
        audioGroups: List<Tracks.Group>,
        groupIndex: Int,
        trackIndex: Int,
    ) {
        val paramsBuilder = exoPlayer.trackSelectionParameters.buildUpon()

        paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        val group = audioGroups[groupIndex]
        paramsBuilder.setOverrideForType(
            TrackSelectionOverride(group.mediaTrackGroup, trackIndex),
        )

        exoPlayer.trackSelectionParameters = paramsBuilder.build()
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Subtitle selection
    // ──────────────────────────────────────────────────────────────────────

    /** Show a dialog listing available subtitle tracks for the current media. */
    private fun showSubtitleSelector() {
        val exoPlayer = player ?: return
        val ctx = context ?: return

        val textGroups = exoPlayer.currentTracks.groups.filter { group ->
            group.type == C.TRACK_TYPE_TEXT
        }

        if (textGroups.isEmpty()) {
            Toast.makeText(ctx, R.string.vod_no_subtitles_available, Toast.LENGTH_SHORT).show()
            return
        }

        data class TrackChoice(
            val label: String,
            val groupIndex: Int,
            val trackIndex: Int,
            val isSelected: Boolean,
        )

        val choices = mutableListOf<TrackChoice>()

        // "Off" option
        val allDeselected = textGroups.none { group ->
            (0 until group.length).any { group.isTrackSelected(it) }
        }
        choices.add(TrackChoice(getString(R.string.vod_subtitle_off), -1, -1, allDeselected))

        textGroups.forEachIndexed { groupIdx, group ->
            for (trackIdx in 0 until group.length) {
                val format = group.getTrackFormat(trackIdx)
                val lang = format.language?.uppercase() ?: "???"
                val trackLabel = format.label ?: lang
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
            .show()
    }

    /**
     * Apply the user's subtitle choice.
     * [groupIndex] == -1 means "turn subtitles off".
     */
    private fun applySubtitleSelection(
        exoPlayer: ExoPlayer,
        textGroups: List<Tracks.Group>,
        groupIndex: Int,
        trackIndex: Int,
    ) {
        val paramsBuilder = exoPlayer.trackSelectionParameters.buildUpon()

        if (groupIndex < 0) {
            // Disable all text tracks
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
    //  Live-TV overlay helpers (unchanged)
    // ──────────────────────────────────────────────────────────────────────

    private fun bindOverlay() {
        updateOverlay(overlayNumber, overlayTitle, overlayMeta)
        bottomTitleView.text = overlayTitle
        bottomMetaView.text = overlayMeta
        bottomGuideView.text = "TV guide"
        bottomHistoryView.text = "History"
        bottomActionOneView.text = "Favorites"
        bottomActionTwoView.text = "Recent"
        bottomActionThreeView.text = overlayNumber
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
        handler.postDelayed(hideOverlayRunnable, OVERLAY_DURATION_MS)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  MediaItem creation (unchanged)
    // ──────────────────────────────────────────────────────────────────────

    private fun createMediaItem(url: String): MediaItem {
        return when {
            url.contains(".m3u8", ignoreCase = true) -> {
                MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
            }

            isChannelProxyUrl(url) -> {
                MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
            }

            url.contains("/live/", ignoreCase = true) -> {
                MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(MimeTypes.VIDEO_MP2T)
                    .build()
            }

            url.contains("stream-proxy", ignoreCase = true) || url.endsWith(".ts", ignoreCase = true) -> {
                MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(MimeTypes.VIDEO_MP2T)
                    .build()
            }

            url.contains("/movie/", ignoreCase = true) ||
                url.contains("/series/", ignoreCase = true) ||
                url.endsWith(".mp4", ignoreCase = true) ||
                url.endsWith(".mkv", ignoreCase = true) ||
                url.endsWith(".avi", ignoreCase = true) -> {
                MediaItem.Builder()
                    .setUri(url)
                    .build()
            }

            else -> MediaItem.fromUri(url)
        }
    }

    private fun isChannelProxyUrl(url: String): Boolean {
        val normalized = url.substringBefore('?')
        return CHANNEL_PROXY_REGEX.containsMatchIn(normalized)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Playback error handling
    // ──────────────────────────────────────────────────────────────────────

    private fun handlePlaybackError(error: PlaybackException? = null) {
        if (isReleasing) return

        val errorMessage = error?.toString() ?: ""
        val isCodecIncompatible = errorMessage.contains("NO_EXCEEDS_CAPABILITIES") ||
            errorMessage.contains("Decoder failed") ||
            errorMessage.contains("dolby-vision")

        if (isCodecIncompatible) {
            Log.w(TAG, "Error de codec incompatible detectado: $errorMessage")
            val ctx = context
            if (ctx != null) {
                Toast.makeText(
                    ctx,
                    "Este contenido 4K Dolby Vision no es compatible con este dispositivo",
                    Toast.LENGTH_LONG
                ).show()
            }
            releasePlayer()
            activity?.onBackPressedDispatcher?.onBackPressed()
            return
        }

        if (retryCount < MAX_RETRIES) {
            retryCount += 1
            Log.d(TAG, "Reintentando reproduccion ($retryCount/$MAX_RETRIES)")
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
                    }
                }
            }, RETRY_DELAY_MS)
        } else {
            handler.postDelayed({
                if (!isReleasing) {
                    releasePlayer()
                    initializePlayer()
                }
            }, FORCE_RESTART_DELAY_MS)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Key handling – dispatches to VOD or Live path
    // ──────────────────────────────────────────────────────────────────────

    private fun handleKeyPress(keyCode: Int): Boolean {
        return if (isVodMode) handleVodKeyPress(keyCode) else handleLiveKeyPress(keyCode)
    }

    /** D-pad handling for VOD: seek, play/pause, subtitles, back. */
    private fun handleVodKeyPress(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                seekRelative(-VOD_SEEK_INCREMENT_MS)
                playerView.showController()
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                seekRelative(VOD_SEEK_INCREMENT_MS)
                playerView.showController()
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            -> {
                player?.let { exoPlayer ->
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                }
                playerView.showController()
                true
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            -> {
                // Let Media3 controller handle focus navigation between buttons
                playerView.showController()
                false
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let { exoPlayer ->
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                }
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player?.play()
                true
            }

            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player?.pause()
                true
            }

            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
            -> {
                seekRelative(-VOD_SEEK_INCREMENT_MS)
                playerView.showController()
                true
            }

            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
            -> {
                seekRelative(VOD_SEEK_INCREMENT_MS)
                playerView.showController()
                true
            }

            KeyEvent.KEYCODE_BACK -> {
                releasePlayer()
                true
            }

            else -> {
                // Any other key: show controller briefly
                playerView.showController()
                false
            }
        }
    }

    /** D-pad handling for live TV (original behavior). */
    private fun handleLiveKeyPress(keyCode: Int): Boolean {
        mapDigit(keyCode)?.let { digit ->
            appendDigit(digit)
            return true
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                showOverlayTemporarily()
                onNavigateChannel(1)
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                showOverlayTemporarily()
                onNavigateChannel(-1)
                true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                showOverlayTemporarily()
                onNavigateOption(-1)
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                showOverlayTemporarily()
                onNavigateOption(1)
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            -> {
                showOverlayTemporarily()
                player?.let { exoPlayer ->
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                }
                true
            }

            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BOOKMARK,
            -> {
                val favoriteEnabled = onToggleFavorite()
                updateOverlay(
                    overlayNumber,
                    overlayTitle,
                    if (favoriteEnabled) "Guardado en favoritos" else "Quitado de favoritos",
                )
                showOverlayTemporarily()
                true
            }

            KeyEvent.KEYCODE_GUIDE -> {
                val opened = onOpenFavorites()
                if (!opened) {
                    updateOverlay(overlayNumber, overlayTitle, "No tienes favoritos guardados")
                    showOverlayTemporarily()
                }
                true
            }

            KeyEvent.KEYCODE_INFO -> {
                val opened = onOpenRecents()
                if (!opened) {
                    updateOverlay(overlayNumber, overlayTitle, "No hay ultimos canales guardados")
                    showOverlayTemporarily()
                }
                true
            }

            KeyEvent.KEYCODE_BACK -> {
                releasePlayer()
                true
            }

            else -> false
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Digit buffer for direct channel entry (live only)
    // ──────────────────────────────────────────────────────────────────────

    private fun mapDigit(keyCode: Int): Int? {
        return when (keyCode) {
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> 0
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 1
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 6
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> 7
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> 8
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> 9
            else -> null
        }
    }

    private fun appendDigit(digit: Int) {
        if (digitBuffer.length >= 4) {
            digitBuffer.clear()
        }

        digitBuffer.append(digit)
        updateOverlay(
            "CH ${digitBuffer}",
            "Cambio directo",
            "Suelta y espera para sintonizar",
        )
        showOverlayTemporarily()
        handler.removeCallbacks(commitDigitsRunnable)
        handler.postDelayed(commitDigitsRunnable, DIRECT_ZAP_DELAY_MS)
    }

    private val commitDigitsRunnable = Runnable {
        val value = digitBuffer.toString().toIntOrNull()
        digitBuffer.clear()
        if (value == null || value <= 0) return@Runnable

        val changed = onDirectChannelNumber(value)
        if (!changed) {
            updateOverlay("CH $value", "Canal no encontrado", "Prueba con otra numeracion")
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
        releasePlayer()
    }

    private fun releasePlayer() {
        isReleasing = true
        isPlayerInitialized = false
        handler.removeCallbacksAndMessages(null)

        // IMPORTANT: Detach the PlayerView BEFORE releasing the ExoPlayer.
        // Otherwise the DefaultTimeBar / position TextView can still query
        // the player on the MediaCodec_loop thread via Util.getStringForTime(),
        // hitting a stale JNI reference and causing a native abort.
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

        activity?.findViewById<FrameLayout>(R.id.player_container)?.visibility = View.GONE
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
        if (::overlayView.isInitialized) {
            overlayView.visibility = View.GONE
        }
        if (::bottomPanelView.isInitialized) {
            bottomPanelView.visibility = View.GONE
        }
    }

    private inner class PlayerListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    retryCount = 0
                    isPlayerInitialized = true
                    updateTrackButtonStates()
                }

                Player.STATE_BUFFERING -> retryCount = 0
                else -> Unit
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateTrackButtonStates()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Error de reproduccion: ${error.message}", error)
            handlePlaybackError(error)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Auto-resume only for live TV; VOD should respect user pause.
            if (!isVodMode && !isPlaying && player?.playbackState == Player.STATE_READY && !isReleasing) {
                handler.postDelayed({
                    if (player != null && !isReleasing) {
                        player?.play()
                    }
                }, 1_000)
            }
        }
    }

    companion object {
        private const val TAG = "PlayerFragment"
        private const val MAX_RETRIES = 8
        private const val RETRY_DELAY_MS = 2_000L
        private const val FORCE_RESTART_DELAY_MS = 3_000L
        private const val OVERLAY_DURATION_MS = 3_000L
        private const val DIRECT_ZAP_DELAY_MS = 1_500L
        private const val VOD_SEEK_INCREMENT_MS = 10_000L
        private const val VOD_CONTROLLER_TIMEOUT_MS = 5_000
        private val CHANNEL_PROXY_REGEX = Regex("https?://[^/]+/[^/]+/[^/]+/\\d+$", RegexOption.IGNORE_CASE)
    }
}
