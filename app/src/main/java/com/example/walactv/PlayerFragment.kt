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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.media3.common.C
import kotlinx.coroutines.CoroutineScope
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
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide

internal fun isFatalPlaybackErrorForDevice(errorMessage: String): Boolean {
    return errorMessage.contains("NO_EXCEEDS_CAPABILITIES") ||
            errorMessage.contains("Decoder failed") ||
            errorMessage.contains("dolby-vision")
}

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
    private val onOpenGuide: ((String?) -> Unit)? = null,
    private val onNextEpisode: (() -> Unit)? = null,
    private val allSeriesEpisodes: List<CatalogItem> = emptyList(),
    private val currentEpisode: CatalogItem? = null,
    private val streamOptionLabels: List<String> = emptyList(),
    private val currentOptionIndex: Int = 0,
    private val overlayLogoUrl: String = "",
    private val isFavorite: Boolean = false,
    private val contentId: String = "",
    private val onPlayerClosed: (() -> Unit)? = null,
) : Fragment() {

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

    // Tracks current favorite state for visual toggle
    private var isFavoriteState: Boolean = isFavorite

    // Watch progress tracking
    private var watchProgressRepo: WatchProgressRepository? = null
    private var lastSavedProgressMs: Long = 0

    // ── Tracks the current option index locally so UP/DOWN updates it ──
    private var liveOptionIndex: Int = currentOptionIndex

    private var retryCount: Int = 0
    private var isPlayerInitialized: Boolean = false
    private var isReleasing: Boolean = false
    private var closedByHost: Boolean = false
    private lateinit var trackSelector: DefaultTrackSelector

    /** True when the content is a movie or series (VOD mode). */
    private val isVodMode: Boolean
        get() = contentKind == ContentKind.MOVIE || contentKind == ContentKind.SERIES

    /** True when this is an event (multiple stream options, UP/DOWN navigation). */
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
            watchProgressRepo = WatchProgressRepository(requireContext())
            setupVodMode()
        } else {
            setupLiveMode(view)
        }

        view.isFocusable = true
        view.isFocusableInTouchMode = true

        return view
    }

    /** Configure the player view for VOD (movies/series) with Media3 controller. */
    private fun setupVodMode() {
        playerView.useController = true
        playerView.controllerShowTimeoutMs = VOD_CONTROLLER_TIMEOUT_MS
        playerView.controllerAutoShow = true
        playerView.requestFocus()

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

    /** Configure the player view for live TV / events with the existing custom overlay. */
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

        // IMPORTANT: find indicator inside channel_overlay, not at root level
        val overlayLayout = view.findViewById<LinearLayout>(R.id.channel_overlay)
        optionIndicatorView = overlayLayout.findViewById(R.id.channel_option_indicator)
        optionsListLayout = overlayLayout.findViewById(R.id.channel_options_list)

        // Show stream options button only for events with multiple streams
        if (isEventMode && streamOptionLabels.size > 1) {
            btnChannel?.visibility = View.VISIBLE
        }

        bindLiveActionButtons()
        bindOptionIndicator()
        bindOverlay()
        updateFavoriteIcon()
    }

    /** Wire live TV action buttons (guide, favorites, channel). */
    private fun bindLiveActionButtons() {
        btnGuide?.setOnClickListener {
            Log.d(TAG, "btnGuide CLICK FIRED, onOpenGuide=${onOpenGuide != null}")
            onOpenGuide?.invoke(null)
        }
        btnFavorites?.setOnClickListener {
            val nowFavorite = onToggleFavorite()
            isFavoriteState = nowFavorite
            updateFavoriteIcon()
            showOverlayTemporarily()
        }
        btnChannel?.setOnClickListener {
            showOverlayTemporarily()
            showOptionsList()
        }
    }

    /** Update the favorites star icon based on current state. */
    private fun updateFavoriteIcon() {
        btnFavoritesIcon?.setImageResource(
            if (isFavoriteState) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        if (!isPlayerInitialized) {
            initializePlayer()
        }
        // Restart overlay timer AFTER initializePlayer (which clears all handler callbacks)
        if (!isVodMode) showOverlayTemporarily()
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

    private val progressSaveRunnable = object : Runnable {
        override fun run() {
            if (player != null && !isReleasing && isVodMode && contentId.isNotBlank()) {
                saveWatchProgress()
                handler.postDelayed(this, PROGRESS_SAVE_INTERVAL_MS)
            }
        }
    }

    private fun bindVodControls() {
        playerView.findViewById<ImageButton>(R.id.vod_btn_audio)?.setOnClickListener {
            showAudioSelector()
        }

        playerView.findViewById<ImageButton>(R.id.vod_btn_subtitles)?.setOnClickListener {
            showSubtitleSelector()
        }

        val nextBtn = playerView.findViewById<ImageButton>(R.id.vod_btn_next)
        if (contentKind == ContentKind.SERIES && onNextEpisode != null) {
            nextBtn?.visibility = View.VISIBLE
            nextBtn?.setOnClickListener { onNextEpisode.invoke() }
        } else {
            nextBtn?.visibility = View.GONE
        }

        handler.removeCallbacks(timeUpdateRunnable)
        handler.post(timeUpdateRunnable)

        // Start periodic progress saving
        if (contentId.isNotBlank()) {
            handler.removeCallbacks(progressSaveRunnable)
            handler.postDelayed(progressSaveRunnable, PROGRESS_SAVE_INTERVAL_MS)
        }
    }

    /** Save current watch progress to API (VOD only). */
    private fun saveWatchProgress() {
        val exoPlayer = player ?: return
        if (contentId.isBlank() || !isVodMode) return
        val position = exoPlayer.currentPosition
        val duration = exoPlayer.duration
        if (duration <= 0 || position <= 0) return
        // Only save if position changed meaningfully (> 5s since last save)
        if (kotlin.math.abs(position - lastSavedProgressMs) < 5_000) return
        lastSavedProgressMs = position

        val contentType = when (contentKind) {
            ContentKind.MOVIE -> "movie"
            ContentKind.SERIES -> "series"
            else -> return
        }

        val repo = watchProgressRepo ?: return
        CoroutineScope(Dispatchers.IO).launch {
            repo.saveProgress(
                contentId = contentId,
                contentType = contentType,
                positionMs = position,
                durationMs = duration,
                title = overlayTitle,
                seriesName = currentSeriesEpisode?.seriesName,
                seasonNumber = currentSeriesEpisode?.seasonNumber,
                episodeNumber = currentSeriesEpisode?.episodeNumber,
            )
        }
    }

    /** Restore watch progress from API (VOD only). */
    private fun restoreWatchProgress() {
        if (contentId.isBlank() || !isVodMode) return
        val repo = watchProgressRepo ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val progress = repo.getProgress(contentId)
                if (progress != null && progress.positionMs > 60_000 && !progress.isCompleted) {
                    withContext(Dispatchers.Main) {
                        player?.seekTo(progress.positionMs)
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

        val audioTrackCount = exoPlayer.currentTracks.groups
            .filter { group -> group.type == C.TRACK_TYPE_AUDIO }
            .sumOf { group -> group.length }
        val hasSelectableAudioTracks = isAudioSelectorEnabled(audioTrackCount)

        val hasSubtitleTracks = exoPlayer.currentTracks.groups.any { group ->
            group.type == C.TRACK_TYPE_TEXT && group.length > 0
        }

        val audioButton = playerView.findViewById<ImageButton>(R.id.vod_btn_audio)
        val subtitleButton = playerView.findViewById<ImageButton>(R.id.vod_btn_subtitles)

        audioButton?.isEnabled = hasSelectableAudioTracks
        audioButton?.alpha = if (hasSelectableAudioTracks) 1.0f else 0.4f

        subtitleButton?.isEnabled = hasSubtitleTracks
        subtitleButton?.alpha = if (hasSubtitleTracks) 1.0f else 0.4f
    }

    private fun seekRelative(deltaMs: Long) {
        val exoPlayer = player ?: return
        if (isReleasing) return
        val duration = exoPlayer.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        if (exoPlayer.playbackState != Player.STATE_READY &&
            exoPlayer.playbackState != Player.STATE_BUFFERING) return
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0, duration)
        exoPlayer.seekTo(target)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Audio / Subtitle selectors (unchanged)
    // ──────────────────────────────────────────────────────────────────────

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
                val lang = normalizeLanguageCode(format.language)
                val channelCount = format.channelCount
                val displayLanguage = languageDisplayLabel(lang)
                val label = if (channelCount > 0) {
                    "$displayLanguage ($channelCount ch)"
                } else {
                    format.label ?: displayLanguage
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

    private fun applyAudioSelection(
        exoPlayer: ExoPlayer,
        audioGroups: List<Tracks.Group>,
        groupIndex: Int,
        trackIndex: Int,
    ) {
        val group = audioGroups[groupIndex]
        if (contentKind == ContentKind.SERIES && allSeriesEpisodes.isNotEmpty()) {
            val selectedFormat = group.getTrackFormat(trackIndex)
            val trackLanguage = selectedFormat.language ?: return
            val languageCode = normalizeLanguageCode(trackLanguage)

            val current = currentSeriesEpisode
            if (current != null && languageCode != normalizeLanguageCode(current.idioma)) {
                val switched = switchToEpisodeWithLanguage(languageCode)
                if (switched) return
            }
        }

        val paramsBuilder = exoPlayer.trackSelectionParameters.buildUpon()
        paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        paramsBuilder.setOverrideForType(
            TrackSelectionOverride(group.mediaTrackGroup, trackIndex),
        )
        exoPlayer.trackSelectionParameters = paramsBuilder.build()
    }

    private fun switchToEpisodeWithLanguage(targetLanguage: String): Boolean {
        val current = currentSeriesEpisode ?: return false
        val equivalentEpisode = allSeriesEpisodes.findEquivalentSeriesEpisode(current, targetLanguage)

        if (equivalentEpisode != null) {
            val stream = equivalentEpisode.streamOptions.firstOrNull() ?: return false
            currentSeriesEpisode = equivalentEpisode

            player?.let { exoPlayer ->
                val wasPlaying = exoPlayer.isPlaying
                val position = exoPlayer.currentPosition

                exoPlayer.setMediaItem(createMediaItem(stream.url))
                exoPlayer.prepare()
                exoPlayer.seekTo(position)
                exoPlayer.playWhenReady = wasPlaying

                updateDisplayedMetadata(
                    title = equivalentEpisode.title,
                    meta = equivalentEpisode.description.ifBlank { stream.label },
                )
            }
            return true
        } else {
            val ctx = context ?: return false
            Toast.makeText(ctx, R.string.episode_not_available_in_language, Toast.LENGTH_SHORT).show()
            return false
        }
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

    /**
     * Refresh the indicator text and the options list (if visible)
     * after [liveOptionIndex] has been updated.
     */
    private fun updateOptionIndicator() {
        val total = streamOptionLabels.size
        if (total <= 1) return
        // e.g. "2 / 3"
        optionIndicatorView?.text = "${liveOptionIndex + 1} / $total"
        optionIndicatorView?.visibility = View.VISIBLE

        // If the options list is currently shown, refresh it so the
        // highlighted entry moves to the new index.
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

        // Load channel logo with Glide
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
        Log.d(TAG, "FAV_MENU_FOCUS: guide=$g fav=$f ch=$c")
        return g || f || c
    }

    fun isOverlayMenuFocused(): Boolean =
        isMenuFocused() || (::overlayView.isInitialized && overlayView.visibility == View.VISIBLE)

    fun isOverlayVisible(): Boolean =
        ::overlayView.isInitialized && overlayView.visibility == View.VISIBLE

    private fun hideOverlay() {
        handler.removeCallbacks(hideOverlayRunnable)
        hideOverlayRunnable.run()
    }

    fun hideOverlayMenu() {
        handler.removeCallbacks(hideOverlayRunnable)
        if (::overlayView.isInitialized) overlayView.visibility = View.GONE
        if (::bottomPanelView.isInitialized) bottomPanelView.visibility = View.GONE
    }

    // ──────────────────────────────────────────────────────────────────────
    //  MediaItem creation
    // ──────────────────────────────────────────────────────────────────────

    private fun createMediaItem(url: String): MediaItem {
        return when {
            url.contains(".m3u8", ignoreCase = true) -> {
                MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build()
            }
            isChannelProxyUrl(url) -> {
                MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build()
            }
            url.contains("/live/", ignoreCase = true) -> {
                MediaItem.Builder().setUri(url).setMimeType(MimeTypes.VIDEO_MP2T).build()
            }
            url.contains("stream-proxy", ignoreCase = true) || url.endsWith(".ts", ignoreCase = true) -> {
                MediaItem.Builder().setUri(url).setMimeType(MimeTypes.VIDEO_MP2T).build()
            }
            url.contains("/movie/", ignoreCase = true) ||
                    url.contains("/series/", ignoreCase = true) ||
                    url.endsWith(".mp4", ignoreCase = true) ||
                    url.endsWith(".mkv", ignoreCase = true) ||
                    url.endsWith(".avi", ignoreCase = true) -> {
                MediaItem.Builder().setUri(url).build()
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
        val isCodecIncompatible = isFatalPlaybackErrorForDevice(errorMessage)

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
    //  Key handling
    // ──────────────────────────────────────────────────────────────────────

    fun dispatchKeyToPlayer(event: KeyEvent): Boolean = handleKeyPress(event)

    private fun handleKeyPress(event: KeyEvent): Boolean =
        if (isVodMode) handleVodKeyPress(event) else handleLiveKeyPress(event)

    /** D-pad handling for VOD: seek, play/pause, back. */
    private fun handleVodKeyPress(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                seekRelative(-VOD_SEEK_INCREMENT_MS); playerView.showController(); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                seekRelative(VOD_SEEK_INCREMENT_MS); playerView.showController(); true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }
                playerView.showController(); true
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                playerView.showController(); false
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }; true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY  -> { player?.play(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.pause(); true }
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                seekRelative(-VOD_SEEK_INCREMENT_MS); playerView.showController(); true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                seekRelative(VOD_SEEK_INCREMENT_MS); playerView.showController(); true
            }
            KeyEvent.KEYCODE_BACK -> { releasePlayer(); true }
            else -> { playerView.showController(); false }
        }
    }

    /**
     * D-pad handling for live TV and events.
     *
     * CHANNELS  → LEFT/RIGHT changes channel (onNavigateChannel ±1)
     *             UP/DOWN    changes stream option (onNavigateOption ±1)
     *
     * EVENTS    → UP/DOWN    changes stream option (onNavigateOption ±1)
     *             LEFT/RIGHT are ignored (no channel lineup for events)
     */
    private fun handleLiveKeyPress(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        // Digits only apply to channels
        if (contentKind == ContentKind.CHANNEL) {
            mapDigit(keyCode)?.let { digit ->
                appendDigit(digit)
                return true
            }
        }

        return when (keyCode) {

            // ── UP : previous stream option (both channels and events) ─────
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isMenuFocused()) return true
                if (::overlayView.isInitialized && overlayView.visibility == View.VISIBLE) {
                    handler.removeCallbacks(hideOverlayRunnable)
                    val focusResult = btnGuide?.requestFocus() ?: false
                    Log.d(TAG, "FAV_UP: btnGuide.requestFocus()=$focusResult guideHasFocus=${btnGuide?.hasFocus()} guideShown=${btnGuide?.isShown} guideAttached=${btnGuide?.isAttachedToWindow}")
                    return true
                }
                val newIndex = liveOptionIndex - 1
                if (streamOptionLabels.size > 1 && newIndex >= 0) {
                    liveOptionIndex = newIndex
                    updateOptionIndicator()
                    showOptionsList()
                    showOverlayTemporarily()
                    onNavigateOption(-1)
                }
                true
            }

            // ── DOWN : next stream option (both channels and events) ───────
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isMenuFocused()) return true
                if (::overlayView.isInitialized && overlayView.visibility == View.VISIBLE) {
                    handler.removeCallbacks(hideOverlayRunnable)
                    val focusResult = btnGuide?.requestFocus() ?: false
                    Log.d(TAG, "FAV_DOWN: btnGuide.requestFocus()=$focusResult guideHasFocus=${btnGuide?.hasFocus()} guideShown=${btnGuide?.isShown} guideAttached=${btnGuide?.isAttachedToWindow}")
                    return true
                }
                val newIndex = liveOptionIndex + 1
                if (streamOptionLabels.size > 1 && newIndex < streamOptionLabels.size) {
                    liveOptionIndex = newIndex
                    updateOptionIndicator()
                    showOptionsList()
                    showOverlayTemporarily()
                    onNavigateOption(1)
                }
                true
            }

            // ── RIGHT : next item in lineup (channels and events) ──────────
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isMenuFocused()) return false
                showOverlayTemporarily()
                onNavigateChannel(1)
                true
            }

            // ── LEFT : previous item in lineup (channels and events) ───────
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isMenuFocused()) return false
                showOverlayTemporarily()
                onNavigateChannel(-1)
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
                Log.d(TAG, "FAV_CENTER: focusedButton=${focusedButton?.id} guide=${btnGuide?.hasFocus()} fav=${btnFavorites?.hasFocus()} ch=${btnChannel?.hasFocus()}")
                if (focusedButton != null) {
                    focusedButton.performClick()
                    return true
                }
                showOverlayTemporarily()
                true
            }

            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BOOKMARK -> {
                val nowFavorite = onToggleFavorite()
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
                val opened = onOpenFavorites()
                if (!opened) {
                    updateOverlay(overlayNumber, overlayTitle, getString(R.string.live_no_favorites))
                    showOverlayTemporarily()
                }
                true
            }

            KeyEvent.KEYCODE_INFO -> {
                val opened = onOpenRecents()
                if (!opened) {
                    updateOverlay(overlayNumber, overlayTitle, getString(R.string.live_no_recents))
                    showOverlayTemporarily()
                }
                true
            }

            KeyEvent.KEYCODE_BACK -> {
                Log.d(TAG, "FAV_BACK_FRAG: isMenuFocused=${isMenuFocused()} THIS_SHOULD_NOT_BE_REACHED")
                if (isMenuFocused()) {
                    playerView.requestFocus()
                    hideOverlay()
                    true
                } else {
                    releasePlayer(); true
                }
            }

            else -> false
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Digit buffer (channels only)
    // ──────────────────────────────────────────────────────────────────────

    private fun mapDigit(keyCode: Int): Int? = when (keyCode) {
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
        val changed = onDirectChannelNumber(value)
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
        releasePlayer()
    }

    fun closeFromHost() {
        Log.d(TAG, "closeFromHost called")
        closedByHost = true
        releasePlayer()
    }

    private fun releasePlayer() {
        Log.d(TAG, "releasePlayer: isReleasing=$isReleasing, closedByHost=$closedByHost")

        // Save watch progress before releasing (VOD only)
        if (isVodMode && contentId.isNotBlank()) {
            saveWatchProgress()
        }

        isReleasing = true
        isPlayerInitialized = false
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

        activity?.findViewById<FrameLayout>(R.id.player_container)?.visibility = View.GONE

        if (!closedByHost) {
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
            when (playbackState) {
                Player.STATE_READY -> {
                    retryCount = 0
                    isPlayerInitialized = true
                    updateTrackButtonStates()
                    if (isVodMode) {
                        playerView.requestFocus()
                        playerView.showController()
                        if (!progressRestored) {
                            progressRestored = true
                            restoreWatchProgress()
                        }
                    } else {
                        // Auto-show overlay when playback starts (TiviMate-style)
                        showOverlayTemporarily()
                    }
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
            if (!isVodMode && !isPlaying && player?.playbackState == Player.STATE_READY && !isReleasing) {
                handler.postDelayed({
                    if (player != null && !isReleasing) player?.play()
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
        private const val PROGRESS_SAVE_INTERVAL_MS = 30_000L
        private val CHANNEL_PROXY_REGEX =
            Regex("https?://[^/]+/[^/]+/[^/]+/\\d+$", RegexOption.IGNORE_CASE)
    }
}
