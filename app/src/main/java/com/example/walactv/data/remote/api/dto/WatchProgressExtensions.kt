package com.example.walactv.data.remote.api.dto

/**
 * Computed properties for WatchProgress DTOs. Kept as extensions so the DTO
 * stays a clean serialization model.
 */

val WatchProgressDto.progressPercent: Int
    get() {
        val dur = durationMs ?: 0L
        val pos = positionMs ?: 0L
        return if (dur > 0) ((pos * 100) / dur).toInt() else 0
    }

val WatchProgressDto.isCompleted: Boolean
    get() {
        val dur = durationMs ?: 0L
        val pos = positionMs ?: 0L
        return dur > 0 && pos >= dur * 95 / 100
    }

val WatchProgressDto.shouldRestoreProgress: Boolean
    get() = (isWatched != true) && (positionMs ?: 0L) > 60_000L && !isCompleted
