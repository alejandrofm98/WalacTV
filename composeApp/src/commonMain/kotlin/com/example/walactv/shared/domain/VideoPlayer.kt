package com.example.walactv.shared.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface VideoPlayer {
    fun play(url: String, headers: Map<String, String> = emptyMap())
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun release()

    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val isPlaying: StateFlow<Boolean>
    val error: StateFlow<PlaybackError?>
    val state: StateFlow<PlayerState>
}

enum class PlayerState {
    IDLE,
    BUFFERING,
    READY,
    PLAYING,
    PAUSED,
    ERROR,
    ENDED,
}

class StubVideoPlayer : VideoPlayer {
    override val positionMs = MutableStateFlow(0L)
    override val durationMs = MutableStateFlow(0L)
    override val isPlaying = MutableStateFlow(false)
    override val error = MutableStateFlow<PlaybackError?>(null)
    override val state = MutableStateFlow(PlayerState.IDLE)

    override fun play(url: String, headers: Map<String, String>) {
        println("StubVideoPlayer: play($url)")
    }
    override fun pause() {}
    override fun resume() {}
    override fun stop() {}
    override fun seekTo(positionMs: Long) {}
    override fun setVolume(volume: Float) {}
    override fun release() {}
}
