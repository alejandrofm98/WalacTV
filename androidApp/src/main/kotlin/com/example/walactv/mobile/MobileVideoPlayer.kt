@file:Suppress("UnsafeOptInUsageError")
package com.example.walactv.mobile

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.walactv.shared.domain.PlayerState
import com.example.walactv.shared.domain.PlaybackError
import com.example.walactv.shared.domain.PlaybackErrorType
import com.example.walactv.shared.domain.VideoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MobileVideoPlayer(context: Context) : VideoPlayer {
    val exoPlayer = ExoPlayer.Builder(context).build()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: MutableStateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: MutableStateFlow<Long> = _durationMs

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: MutableStateFlow<Boolean> = _isPlaying

    private val _error = MutableStateFlow<PlaybackError?>(null)
    override val error: MutableStateFlow<PlaybackError?> = _error

    private val _state = MutableStateFlow(PlayerState.IDLE)
    override val state: MutableStateFlow<PlayerState> = _state

    private val scope = CoroutineScope(Dispatchers.Main)
    private var positionPollingJob: Job? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> _state.value = PlayerState.BUFFERING
                    Player.STATE_READY -> {
                        _state.value = PlayerState.READY
                        _durationMs.value = exoPlayer.duration.coerceAtLeast(0)
                    }
                    Player.STATE_ENDED -> {
                        _state.value = PlayerState.ENDED
                        _isPlaying.value = false
                    }
                    Player.STATE_IDLE -> _state.value = PlayerState.IDLE
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    _state.value = PlayerState.PLAYING
                    startPositionPolling()
                } else {
                    if (_state.value == PlayerState.PLAYING) {
                        _state.value = PlayerState.PAUSED
                    }
                    stopPositionPolling()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val errorType = when {
                    error.errorCode >= PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED &&
                    error.errorCode <= PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackErrorType.NETWORK
                    error.errorCode >= PlaybackException.ERROR_CODE_DECODER_INIT_FAILED &&
                    error.errorCode <= PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> PlaybackErrorType.CODEC_INCOMPATIBLE
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackErrorType.STREAM_UNAVAILABLE
                    else -> PlaybackErrorType.GENERIC
                }
                _error.value = PlaybackError(
                    type = errorType,
                    title = "Error de reproduccion",
                    message = error.message ?: "Error desconocido",
                )
                _state.value = PlayerState.ERROR
            }
        })
    }

    private fun startPositionPolling() {
        stopPositionPolling()
        positionPollingJob = scope.launch {
            while (true) {
                _positionMs.value = exoPlayer.currentPosition.coerceAtLeast(0)
                _durationMs.value = exoPlayer.duration.coerceAtLeast(0)
                delay(250)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }

    override fun play(url: String, headers: Map<String, String>) {
        _error.value = null
        val mediaItem = when {
            url.contains(".m3u8", ignoreCase = true) ->
                MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build()
            url.contains("/live/", ignoreCase = true) || url.endsWith(".ts", ignoreCase = true) ->
                MediaItem.Builder().setUri(url).setMimeType(MimeTypes.VIDEO_MP2T).build()
            else -> MediaItem.fromUri(url)
        }
        if (headers.isNotEmpty()) {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("WalacTV/Mobile")
                .setDefaultRequestProperties(headers)
            val source = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            exoPlayer.setMediaSource(source)
        } else {
            exoPlayer.setMediaItem(mediaItem)
        }
        exoPlayer.prepare()
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
        _state.value = PlayerState.PAUSED
    }

    override fun resume() {
        exoPlayer.play()
    }

    override fun stop() {
        exoPlayer.stop()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    override fun setVolume(volume: Float) {
        exoPlayer.volume = volume
    }

    override fun release() {
        stopPositionPolling()
        exoPlayer.release()
    }
}
