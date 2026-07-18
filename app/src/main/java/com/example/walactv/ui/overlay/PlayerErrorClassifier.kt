package com.example.walactv.ui.overlay

/**
 * Returns true if the given ExoPlayer error message indicates a fatal playback
 * failure for the current device (unsupported codec, dolby-vision, decoder
 * failure). Used by the player to decide between recoverable retries and
 * showing a hard error overlay.
 */
internal fun isFatalPlaybackErrorForDevice(errorMessage: String): Boolean {
    return errorMessage.contains("NO_EXCEEDS_CAPABILITIES") ||
            errorMessage.contains("Decoder failed") ||
            errorMessage.contains("dolby-vision")
}
