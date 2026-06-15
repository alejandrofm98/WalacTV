package com.example.walactv.shared.data

import com.example.walactv.shared.domain.PlaybackError
import com.example.walactv.shared.domain.PlaybackErrorType
import com.example.walactv.shared.domain.PlayerState
import com.example.walactv.shared.domain.VideoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.awt.Component

class DesktopVideoPlayer : VideoPlayer {
    override val positionMs = MutableStateFlow(0L)
    override val durationMs = MutableStateFlow(0L)
    override val isPlaying = MutableStateFlow(false)
    override val error = MutableStateFlow<PlaybackError?>(null)
    override val state = MutableStateFlow(PlayerState.IDLE)

    private var mediaPlayerComponent: EmbeddedMediaPlayerComponent? = null

    private fun ensureInitialized(): EmbeddedMediaPlayerComponent {
        mediaPlayerComponent?.let { return it }

        val component = try {
            EmbeddedMediaPlayerComponent()
        } catch (e: Exception) {
            error.value = PlaybackError(
                type = PlaybackErrorType.GENERIC,
                title = "VLC no encontrado",
                message = "Instala VLC para reproducir contenido en desktop",
            )
            state.value = PlayerState.ERROR
            throw e
        }

        component.mediaPlayer().events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mp: MediaPlayer) {
                isPlaying.value = true
                state.value = PlayerState.PLAYING
            }

            override fun paused(mp: MediaPlayer) {
                isPlaying.value = false
                state.value = PlayerState.PAUSED
            }

            override fun stopped(mp: MediaPlayer) {
                isPlaying.value = false
                state.value = PlayerState.IDLE
                positionMs.value = 0
            }

            override fun finished(mp: MediaPlayer) {
                isPlaying.value = false
                state.value = PlayerState.ENDED
            }

            override fun error(mp: MediaPlayer) {
                error.value = PlaybackError(
                    type = PlaybackErrorType.GENERIC,
                    title = "Error de reproduccion",
                    message = "No se pudo reproducir el contenido",
                )
                state.value = PlayerState.ERROR
            }

            override fun timeChanged(mp: MediaPlayer, newTime: Long) {
                positionMs.value = newTime
            }

            override fun lengthChanged(mp: MediaPlayer, newLength: Long) {
                durationMs.value = newLength
            }

            override fun buffering(mp: MediaPlayer, newCache: Float) {
                if (state.value != PlayerState.PLAYING && state.value != PlayerState.PAUSED) {
                    state.value = PlayerState.BUFFERING
                }
            }
        })

        mediaPlayerComponent = component
        return component
    }

    fun getVideoComponent(): Component {
        return try {
            ensureInitialized().videoSurfaceComponent()
        } catch (e: Exception) {
            error.value = PlaybackError(
                type = PlaybackErrorType.GENERIC,
                title = "VLC no encontrado",
                message = "Instala VLC para reproducir contenido en desktop",
            )
            state.value = PlayerState.ERROR
            javax.swing.JPanel()
        }
    }

    override fun play(url: String, headers: Map<String, String>) {
        try {
            ensureInitialized()
        } catch (_: Exception) {
            return
        }
        error.value = null
        state.value = PlayerState.BUFFERING
        val options = headers.map { (key, value) ->
            when (key.lowercase()) {
                "user-agent" -> ":http-user-agent=$value"
                "referer", "referrer" -> ":http-referrer=$value"
                "origin" -> ":http-origin=$value"
                else -> ":http-header:$key=$value"
            }
        }
        mediaPlayerComponent!!.mediaPlayer().media().play(url, *options.toTypedArray())
    }

    override fun pause() {
        mediaPlayerComponent?.mediaPlayer()?.controls()?.pause()
    }

    override fun resume() {
        mediaPlayerComponent?.mediaPlayer()?.controls()?.play()
    }

    override fun stop() {
        mediaPlayerComponent?.mediaPlayer()?.controls()?.stop()
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayerComponent?.mediaPlayer()?.controls()?.setTime(positionMs)
    }

    override fun setVolume(volume: Float) {
        mediaPlayerComponent?.mediaPlayer()?.audio()?.setVolume((volume * 100).toInt().coerceIn(0, 100))
    }

    override fun release() {
        mediaPlayerComponent?.release()
        mediaPlayerComponent = null
        state.value = PlayerState.IDLE
    }
}
